package com.jack.bytejudge.user.auth.interfaces.internal;

import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Internal protocol object, independent of the browser login body. */
public record IdentityVerificationRequest(String sessionId, String path) {
    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Array, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Object, CoercionAction.Fail))
            .build();

    public static IdentityVerificationRequest parse(byte[] body) {
        try {
            IdentityVerificationRequest request = STRICT.readValue(body, IdentityVerificationRequest.class);
            if (request == null || request.sessionId() == null
                    || request.sessionId().isBlank() || request.sessionId().length() > 256
                    || request.path() == null || !request.path().startsWith("/")
                    || request.path().length() > 256) {
                throw new InvalidRequestException();
            }
            return request;
        } catch (JacksonException malformed) {
            throw new InvalidRequestException();
        }
    }
}
