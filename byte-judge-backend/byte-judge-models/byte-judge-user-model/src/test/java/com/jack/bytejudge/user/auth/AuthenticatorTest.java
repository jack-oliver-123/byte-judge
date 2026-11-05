package com.jack.bytejudge.user.auth;

import com.jack.bytejudge.common.core.security.SessionIdentity;
import com.jack.bytejudge.user.account.domain.AccountRole;
import com.jack.bytejudge.user.account.domain.AccountStatus;
import com.jack.bytejudge.user.auth.application.Authenticator;
import com.jack.bytejudge.user.auth.application.port.AccountLookup;
import com.jack.bytejudge.user.auth.application.port.AuthAudit;
import com.jack.bytejudge.user.auth.domain.AccountDisabledException;
import com.jack.bytejudge.user.auth.domain.InvalidCredentialsException;
import com.jack.bytejudge.user.auth.domain.model.AuthAuditEntry;
import com.jack.bytejudge.user.auth.domain.model.AuthEvent;
import com.jack.bytejudge.user.auth.domain.model.AuthOutcome;
import com.jack.bytejudge.user.auth.domain.model.StoredAccount;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    private final Authenticator authenticator = new Authenticator(accounts, passwords, audit);

    @Test
    void wrongPasswordsAreAuditedAsCredentialRejections() {
        accounts.stored = Optional.of(ACCOUNT);
        passwords.matches = false;

        assertThatThrownBy(() -> authenticator.authenticate("alice", "wrong-password".toCharArray(),
                "request-1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authenticator.authenticate("alice", "wrong-password".toCharArray(),
                "request-2"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(audit.entries).extracting(AuthAuditEntry::reason).containsExactly("CREDENTIALS", "CREDENTIALS");
    }

    @Test
    void anUnknownAccountIsAuditedAsACredentialRejection() {
        accounts.stored = Optional.empty();

        assertThatThrownBy(() -> authenticator.authenticate("nobody", "wrong-password".toCharArray(),
                "request-1"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.reason()).isEqualTo("CREDENTIALS");
            assertThat(entry.account()).isEqualTo("nobody");
        });
    }

    @Test
    void aSuccessfulLoginReturnsTheStoredIdentityAndIsAudited() {
        accounts.stored = Optional.of(ACCOUNT);
        passwords.matches = true;

        SessionIdentity identity = authenticator.authenticate("alice", "correct-horse".toCharArray(),
                "request-1");

        assertThat(identity).isEqualTo(new SessionIdentity(7, "alice", "user", 3));
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(AuthEvent.LOGIN);
            assertThat(entry.outcome()).isEqualTo(AuthOutcome.SUCCESS);
            assertThat(entry.account()).isEqualTo("alice");
        });
    }

    @Test
    void aDisabledAccountWithTheCorrectPasswordIsAuditedAsDisabled() {
        accounts.stored = Optional.of(new StoredAccount(7, "alice", "stored-hash",
                AccountRole.USER, AccountStatus.DISABLED, 3));
        passwords.matches = true;

        assertThatThrownBy(() -> authenticator.authenticate("alice", "correct-horse".toCharArray(),
                "request-1"))
                .isInstanceOf(AccountDisabledException.class);

        assertThat(audit.entries).singleElement()
                .extracting(AuthAuditEntry::reason).isEqualTo("ACCOUNT_DISABLED");
    }

    private static final class FakeAccounts implements AccountLookup {
        private Optional<StoredAccount> stored = Optional.empty();

        @Override
        public Optional<StoredAccount> findByNormalizedAccount(String account) {
            return stored;
        }
    }

    private static final class FakePasswords implements com.jack.bytejudge.user.auth.application.port.PasswordMatcher {
        private boolean matches;

        @Override
        public boolean matches(char[] rawPassword, String storedHash) {
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
}
