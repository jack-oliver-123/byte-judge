package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.RevokedSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisSessionRevocationTest {
    private final RevokedSessionMapper mapper = mock(RevokedSessionMapper.class);
    private final IdentifierGenerator identifiers = mock(IdentifierGenerator.class);
    private final MybatisSessionRevocation revocation = new MybatisSessionRevocation(mapper, identifiers);

    @BeforeEach
    void generateIdentifiers() {
        when(identifiers.nextId(any())).thenReturn(1L);
    }

    @Test
    void repeatedRevocationOfTheSameSessionIsIdempotent() {
        doThrow(new DuplicateKeyException("uk_revoked_session_hash"))
                .when(mapper).insert(anyLong(), any(byte[].class));
        when(mapper.countBySessionIdHash(any(byte[].class))).thenReturn(1);

        assertThatCode(() -> revocation.revoke("session-a")).doesNotThrowAnyException();
    }

    @Test
    void anIdentifierCollisionForAnotherSessionFailsClosed() {
        doThrow(new DuplicateKeyException("PRIMARY"))
                .when(mapper).insert(anyLong(), any(byte[].class));
        when(mapper.countBySessionIdHash(any(byte[].class))).thenReturn(0);

        assertThatThrownBy(() -> revocation.revoke("session-a"))
                .as("a missing tombstone must not be reported as a completed revocation")
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void onlyAThirtyTwoByteDigestIsStored() {
        revocation.revoke("session-a");

        var hash = ArgumentCaptor.forClass(byte[].class);
        verify(mapper).insert(anyLong(), hash.capture());
        assertThat(hash.getValue()).as("a SHA-256 digest, never the raw session identifier").hasSize(32);
    }
}
