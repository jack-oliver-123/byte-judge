package com.jack.bytejudge.user.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.user.auth.application.port.SessionRevocation;
import com.jack.bytejudge.user.auth.infrastructure.persistence.mapper.RevokedSessionMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class MybatisSessionRevocation implements SessionRevocation {
    private final RevokedSessionMapper mapper;
    private final IdentifierGenerator identifiers;

    public MybatisSessionRevocation(RevokedSessionMapper mapper, IdentifierGenerator identifiers) {
        this.mapper = mapper;
        this.identifiers = identifiers;
    }

    @Override
    public boolean isRevoked(String sessionId) {
        return mapper.countBySessionIdHash(digest(sessionId)) > 0;
    }

    @Override
    public void revoke(String sessionId) {
        // The mapper call commits before returning; a storage failure propagates so the caller
        // cannot report a successful logout or a completed rejection.
        mapper.insert(identifiers.nextId("revoked-session").longValue(), digest(sessionId));
    }

    private static byte[] digest(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
