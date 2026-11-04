package com.jack.bytejudge.user.auth.infrastructure.ratelimit;

import com.jack.bytejudge.user.auth.application.port.LoginAttemptLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Atomic Redis policy. A failure increments both dimensions and, when a dimension reaches the
 * threshold, creates its block with SET NX so concurrent and repeated failures cannot extend it.
 * The failure counter is expired together with a newly created block: when the block ends, the
 * next attempt starts from a clean count instead of immediately re-arming the block.
 */
public final class RedisLoginAttemptLimiter implements LoginAttemptLimiter {
    private static final RedisScript<Long> BLOCKED_FOR = RedisScript.of("""
            local account = redis.call('PTTL', KEYS[1])
            local source = redis.call('PTTL', KEYS[2])
            local retry = math.max(account, source)
            if retry <= 0 then
              return 0
            end
            return retry
            """, Long.class);

    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of("""
            local window = tonumber(ARGV[1])
            local threshold = tonumber(ARGV[2])
            local blockDuration = tonumber(ARGV[3])

            local function bump(counterKey, blockKey)
              local count = redis.call('INCR', counterKey)
              if count == 1 then
                redis.call('PEXPIRE', counterKey, window)
              end
              if count >= threshold then
                local created = redis.call('SET', blockKey, '1', 'NX', 'PX', blockDuration)
                if created then
                  -- Keep the counter alive exactly as long as the block; the next window then
                  -- starts from zero after the block releases.
                  redis.call('PEXPIRE', counterKey, blockDuration)
                end
              end
              return count
            end

            local accountCount = bump(KEYS[1], KEYS[2])
            local sourceCount = bump(KEYS[3], KEYS[4])
            if accountCount <= threshold and sourceCount <= threshold then
              return 0
            end
            local accountRetry = redis.call('PTTL', KEYS[2])
            local sourceRetry = redis.call('PTTL', KEYS[4])
            local retry = math.max(accountRetry, sourceRetry)
            if retry <= 0 then
              retry = blockDuration
            end
            return retry
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LoginRateLimitProperties properties;

    public RedisLoginAttemptLimiter(StringRedisTemplate redis, LoginRateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Optional<Duration> blockedFor(String normalizedAccount, String sourceAddress) {
        Long remaining = redis.execute(BLOCKED_FOR,
                List.of(blockKey("account", normalizedAccount), blockKey("source", sourceAddress)));
        return remaining != null && remaining > 0 ? Optional.of(Duration.ofMillis(remaining)) : Optional.empty();
    }

    @Override
    public Optional<Duration> recordFailure(String normalizedAccount, String sourceAddress) {
        Long remaining = redis.execute(RECORD_FAILURE,
                List.of(counterKey("account", normalizedAccount), blockKey("account", normalizedAccount),
                        counterKey("source", sourceAddress), blockKey("source", sourceAddress)),
                Long.toString(properties.window().toMillis()),
                Integer.toString(properties.maxFailures()),
                Long.toString(properties.blockDuration().toMillis()));
        return remaining != null && remaining > 0 ? Optional.of(Duration.ofMillis(remaining)) : Optional.empty();
    }

    private String counterKey(String dimension, String value) {
        return properties.namespace() + ":" + dimension + ":" + value + ":failures";
    }

    private String blockKey(String dimension, String value) {
        return properties.namespace() + ":" + dimension + ":" + value + ":blocked";
    }
}
