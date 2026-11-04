package com.jack.bytejudge.user.auth.infrastructure.ratelimit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis is real: the atomic script, TTLs and concurrent increments are part of the contract. */
class RedisLoginAttemptLimiterTest {
    private static GenericContainer<?> redis;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate template;

    private String namespace;
    private RedisLoginAttemptLimiter limiter;

    @BeforeAll
    static void startRedis() {
        redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.12"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort());
        redis.start();
        var configuration = new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void newPolicy() {
        namespace = "test:login:" + UUID.randomUUID();
        limiter = new RedisLoginAttemptLimiter(template,
                new LoginRateLimitProperties(Duration.ofSeconds(5), 3, Duration.ofSeconds(1), namespace));
    }

    @Test
    void theThresholdFailureArmsTheBlockAndLaterFailuresAreBeyondIt() {
        assertThat(limiter.blockedFor("alice", "127.0.0.1")).isEmpty();

        assertThat(limiter.recordFailure("alice", "127.0.0.1")).as("1st").isEmpty();
        assertThat(limiter.recordFailure("alice", "127.0.0.1")).as("2nd").isEmpty();
        assertThat(limiter.recordFailure("alice", "127.0.0.1")).as("3rd reaches the threshold").isEmpty();

        assertThat(limiter.blockedFor("alice", "127.0.0.1")).isPresent();
        assertThat(limiter.recordFailure("alice", "127.0.0.1")).as("a failure beyond the threshold")
                .isPresent();
    }

    @Test
    void theSourceDimensionIsSharedByDifferentAccountsButDoesNotBlockTheAccountElsewhere() {
        record("account_a", "127.0.0.2");
        record("account_b", "127.0.0.2");
        record("account_c", "127.0.0.2");

        assertThat(limiter.blockedFor("account_d", "127.0.0.2")).as("4th account from the same source")
                .isPresent();
        assertThat(limiter.blockedFor("account_a", "127.0.0.3")).as("a different source on a lightly failed account")
                .isEmpty();
    }

    @Test
    void theAccountDimensionIsSharedByDifferentSourcesButDoesNotBlockTheSourceElsewhere() {
        record("shared_account", "127.0.1.1");
        record("shared_account", "127.0.1.2");
        record("shared_account", "127.0.1.3");

        assertThat(limiter.blockedFor("shared_account", "127.0.1.4")).as("4th source for the same account")
                .isPresent();
        assertThat(limiter.blockedFor("other_account", "127.0.1.1")).as("a different account on a lightly failed source")
                .isEmpty();
    }

    @Test
    void concurrentFailuresCannotExceedTheThreshold() throws Exception {
        int attempts = 20;
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(attempts);
        var admitted = new ArrayList<Optional<Duration>>();
        var executor = Executors.newFixedThreadPool(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        Optional<Duration> result = limiter.recordFailure("burst_account", "127.0.1.9");
                        synchronized (admitted) {
                            admitted.add(result);
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

            assertThat(admitted).hasSize(attempts);
            assertThat(admitted.stream().filter(Optional::isEmpty).count())
                    .as("only the threshold number of failures may be admitted")
                    .isEqualTo(3);
            assertThat(limiter.blockedFor("burst_account", "127.0.1.9")).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void theFailureWindowExpiresAndStartsAgain() throws Exception {
        limiter = new RedisLoginAttemptLimiter(template,
                new LoginRateLimitProperties(Duration.ofMillis(300), 3, Duration.ofMillis(200), namespace));

        record("window_account", "127.0.2.1");
        record("window_account", "127.0.2.1");
        Thread.sleep(600);

        assertThat(limiter.recordFailure("window_account", "127.0.2.1"))
                .as("the post-window failure must start a fresh count instead of reaching the old threshold")
                .isEmpty();
        assertThat(limiter.blockedFor("window_account", "127.0.2.1")).isEmpty();
    }

    @Test
    void laterFailuresDoNotMoveEitherFixedWindow() throws Exception {
        record("fixed_account", "127.0.2.2");
        String accountKey = namespace + ":account:fixed_account:failures";
        String sourceKey = namespace + ":source:127.0.2.2:failures";
        long accountTtl = template.getExpire(accountKey, TimeUnit.MILLISECONDS);
        long sourceTtl = template.getExpire(sourceKey, TimeUnit.MILLISECONDS);

        Thread.sleep(150);
        record("fixed_account", "127.0.2.2");

        assertThat(template.getExpire(accountKey, TimeUnit.MILLISECONDS))
                .as("a later failure must not restart the account window")
                .isPositive().isLessThan(accountTtl - 100);
        assertThat(template.getExpire(sourceKey, TimeUnit.MILLISECONDS))
                .as("a later failure must not restart the source window")
                .isPositive().isLessThan(sourceTtl - 100);
    }

    @Test
    void theBlockExpiresAndAllowsFreshAttempts() throws Exception {
        limiter = new RedisLoginAttemptLimiter(template,
                new LoginRateLimitProperties(Duration.ofSeconds(5), 3, Duration.ofMillis(300), namespace));
        record("recovering_account", "127.0.3.1");
        record("recovering_account", "127.0.3.1");
        record("recovering_account", "127.0.3.1");
        assertThat(limiter.blockedFor("recovering_account", "127.0.3.1")).isPresent();

        Thread.sleep(600);

        assertThat(limiter.blockedFor("recovering_account", "127.0.3.1")).as("block released").isEmpty();
        assertThat(limiter.recordFailure("recovering_account", "127.0.3.1"))
                .as("the failure counter expires with the block, so the next failure is counted from one")
                .isEmpty();
    }

    @Test
    void blockedChecksAndRepeatedFailuresDoNotExtendTheBlock() throws Exception {
        limiter = new RedisLoginAttemptLimiter(template,
                new LoginRateLimitProperties(Duration.ofSeconds(5), 3, Duration.ofMillis(800), namespace));
        record("extend_account", "127.0.4.1");
        record("extend_account", "127.0.4.1");
        record("extend_account", "127.0.4.1");

        Duration first = limiter.blockedFor("extend_account", "127.0.4.1").orElseThrow();
        Thread.sleep(150);
        Duration second = limiter.blockedFor("extend_account", "127.0.4.1").orElseThrow();

        assertThat(second).as("reads must observe a shrinking TTL").isLessThan(first);
        Thread.sleep(800);
        assertThat(limiter.blockedFor("extend_account", "127.0.4.1")).isEmpty();
    }

    @Test
    void anAccountBlockCanExpireWithoutClearingALaterSourceBlock() throws Exception {
        limiter = new RedisLoginAttemptLimiter(template,
                new LoginRateLimitProperties(Duration.ofSeconds(5), 3, Duration.ofMillis(1400), namespace));
        record("early_account", "127.0.6.1");
        record("early_account", "127.0.6.2");
        record("early_account", "127.0.6.3");
        assertThat(limiter.blockedFor("early_account", "127.0.6.4")).isPresent();

        Thread.sleep(600);
        record("another_account", "127.0.6.1");
        record("third_account", "127.0.6.1");
        assertThat(limiter.blockedFor("fresh_account", "127.0.6.1")).isPresent();

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (limiter.blockedFor("early_account", "127.0.6.4").isPresent()
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(limiter.blockedFor("early_account", "127.0.6.4"))
                .as("the earlier account block expires independently").isEmpty();
        assertThat(limiter.blockedFor("early_account", "127.0.6.1"))
                .as("the later source block still limits the expired account").isPresent();
        assertThat(limiter.recordFailure("early_account", "127.0.6.4"))
                .as("the account starts a fresh window on another source").isEmpty();
        assertThat(limiter.blockedFor("fresh_account", "127.0.6.1"))
                .as("a new failure elsewhere must not clear the source block").isPresent();
    }

    @Test
    void keysCarryOnlyAccountAddressAndDimensionMetadata() {
        record("key_account", "127.0.5.1");

        assertThat(template.keys(namespace + ":*")).containsExactlyInAnyOrder(
                namespace + ":account:key_account:failures",
                namespace + ":source:127.0.5.1:failures");
    }

    private void record(String account, String source) {
        limiter.recordFailure(account, source);
    }
}
