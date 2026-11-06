package com.jack.bytejudge.user.auth.interfaces.http.dto;

import com.jack.bytejudge.user.auth.interfaces.http.InvalidRequestException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * The login body is parsed by a strict reader instead of the application-wide mapper: unknown or
 * server-controlled fields, duplicate keys, scalar coercion and trailing content are rejected, so a
 * client can never smuggle a role or authentication version into authentication.
 */
public record LoginRequest(String account, String password) {
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

    public static LoginRequest parse(byte[] body) {
        try {
            LoginRequest request = STRICT.readValue(body, LoginRequest.class);
            if (request == null || request.account() == null || request.password() == null) {
                throw new InvalidRequestException();
            }
            return request;
        } catch (JacksonException malformed) {
            throw new InvalidRequestException();
        }
    }
}
