package com.jack.bytejudge.user.auth;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;
import com.jack.bytejudge.user.auth.application.Authenticator;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.application.port.LoginAttemptLimiter;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.LoginRateLimitedException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatorTest {
    private static final StoredAccount ACCOUNT = new StoredAccount(7, "alice", "stored-hash",
            AccountRole.USER, AccountStatus.NORMAL, 3);

    private final FakeAccounts accounts = new FakeAccounts();
    private final FakePasswords passwords = new FakePasswords();
    private final RecordingAudit audit = new RecordingAudit();
    private final FakeLimiter limiter = new FakeLimiter();
    private final Authenticator authenticator = new Authenticator(accounts, passwords, audit, limiter);

    @Test
    void aBlockedPairIsRejectedBeforeAnyLookupOrPasswordWorkAndIsAudited() {
        limiter.blocked = Optional.of(Duration.ofSeconds(42));

        assertThatThrownBy(() -> authenticator.authenticate(" Alice ", "correct-horse".toCharArray(),
                "request-1", "203.0.113.9"))
                .isInstanceOfSatisfying(LoginRateLimitedException.class,
                        failure -> assertThat(failure.retryAfter()).isEqualTo(Duration.ofSeconds(42)));

        assertThat(accounts.lookups).isZero();
        assertThat(passwords.checks).isZero();
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(AuthEvent.LOGIN);
            assertThat(entry.outcome()).isEqualTo(AuthOutcome.REJECTED);
            assertThat(entry.reason()).isEqualTo("RATE_LIMITED");
            assertThat(entry.account()).as("the limit key uses the authentication normalization").isEqualTo("alice");
            assertThat(entry.userId()).isNull();
            assertThat(entry.requestId()).isEqualTo("request-1");
        });
    }

    @Test
    void theThresholdFailureStaysARejectionAndTheFailureBeyondItBecomesRateLimited() {
        accounts.stored = Optional.of(ACCOUNT);
        passwords.matches = false;
        limiter.recordResults.add(Optional.empty());
        limiter.recordResults.add(Optional.of(Duration.ofMinutes(15)));

        assertThatThrownBy(() -> authenticator.authenticate("alice", "wrong-password".toCharArray(),
                "request-1", "203.0.113.9"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authenticator.authenticate("alice", "wrong-password".toCharArray(),
                "request-2", "203.0.113.9"))
                .isInstanceOfSatisfying(LoginRateLimitedException.class,
                        failure -> assertThat(failure.retryAfter()).isEqualTo(Duration.ofMinutes(15)));

        assertThat(limiter.recordKeys).containsExactly(
                new Subject("alice", "203.0.113.9"), new Subject("alice", "203.0.113.9"));
        assertThat(audit.entries).extracting(AuthAuditEntry::reason).containsExactly("CREDENTIALS", "CREDENTIALS");
    }

    @Test
    void anUnknownAccountAndAWrongPasswordFollowTheSameFailurePath() {
        accounts.stored = Optional.empty();
        limiter.recordResults.add(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate("nobody", "wrong-password".toCharArray(),
                "request-1", "203.0.113.9"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(limiter.recordKeys).containsExactly(new Subject("nobody", "203.0.113.9"));
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo("CREDENTIALS");
            assertThat(entry.account()).isEqualTo("nobody");
        });
    }

    @Test
    void aSuccessfulLoginDoesNotTouchTheFailureCounters() {
        accounts.stored = Optional.of(ACCOUNT);
        passwords.matches = true;

        SessionIdentity identity = authenticator.authenticate("alice", "correct-horse".toCharArray(),
                "request-1", "203.0.113.9");

        assertThat(identity).isEqualTo(new SessionIdentity(7, "alice", "user", 3));
        assertThat(limiter.recordKeys).isEmpty();
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(AuthEvent.LOGIN);
            assertThat(entry.outcome()).isEqualTo(AuthOutcome.SUCCESS);
            assertThat(entry.account()).isEqualTo("alice");
        });
    }

    @Test
    void aDisabledAccountWithTheCorrectPasswordIsNotCountedAsACredentialFailure() {
        accounts.stored = Optional.of(new StoredAccount(7, "alice", "stored-hash",
                AccountRole.USER, AccountStatus.DISABLED, 3));
        passwords.matches = true;

        assertThatThrownBy(() -> authenticator.authenticate("alice", "correct-horse".toCharArray(),
                "request-1", "203.0.113.9"))
                .isInstanceOf(AccountDisabledException.class);

        assertThat(limiter.recordKeys).isEmpty();
        assertThat(audit.entries).singleElement()
                .extracting(AuthAuditEntry::reason).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void aRateLimiterOutagePreventsAuthenticationInsteadOfSkippingTheLimit() {
        accounts.stored = Optional.of(ACCOUNT);
        passwords.matches = true;
        limiter.blockedFailure = new org.springframework.data.redis.RedisConnectionFailureException("down");

        assertThatThrownBy(() -> authenticator.authenticate("alice", "correct-horse".toCharArray(),
                "request-1", "203.0.113.9"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        assertThat(accounts.lookups)
                .as("no credential work may run while the limit cannot be evaluated").isZero();
        assertThat(passwords.checks).isZero();
        assertThat(audit.entries).isEmpty();
    }

    private record Subject(String account, String source) { }

    private static final class FakeAccounts implements AccountLookup {
        private Optional<StoredAccount> stored = Optional.empty();
        private int lookups;

        @Override
        public Optional<StoredAccount> findByNormalizedAccount(String account) {
            lookups++;
            return stored;
        }
    }

    private static final class FakePasswords implements com.jack.bytejudge.user.auth.application.port.PasswordMatcher {
        private boolean matches;
        private int checks;

        @Override
        public boolean matches(char[] rawPassword, String storedHash) {
            checks++;
            return matches;
        }
    }

    private static final class RecordingAudit implements AuthAudit {
        private final List<AuthAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(AuthAuditEntry entry) {
            entries.add(entry);
        }
    }

    private static final class FakeLimiter implements LoginAttemptLimiter {
        private Optional<Duration> blocked = Optional.empty();
        private RuntimeException blockedFailure;
        private final Deque<Optional<Duration>> recordResults = new ArrayDeque<>();
        private final List<Subject> recordKeys = new ArrayList<>();

        @Override
        public Optional<Duration> blockedFor(String normalizedAccount, String sourceAddress) {
            if (blockedFailure != null) {
                throw blockedFailure;
            }
            return blocked;
        }

        @Override
        public Optional<Duration> recordFailure(String normalizedAccount, String sourceAddress) {
            recordKeys.add(new Subject(normalizedAccount, sourceAddress));
            return recordResults.isEmpty() ? Optional.empty() : recordResults.removeFirst();
        }
    }
}
