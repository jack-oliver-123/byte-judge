package com.jack.bytejudge.user.auth.interfaces.http;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * The one strict reader for authentication protocol bodies: unknown fields, duplicate keys,
 * trailing content and scalar coercion into strings are rejected, so a client cannot smuggle a
 * role or an authentication version past the boundary. Each protocol record adds its own
 * field-level rules (required names, formats) after reading; the CLI credential reader is a
 * separate contract and does not share this mapper.
 */
public final class StrictJson {
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

    private StrictJson() {
    }

    /** Reads a protocol body, reporting every violation as {@link InvalidRequestException}. */
    public static <T> T read(byte[] body, Class<T> type) {
        try {
            T value = STRICT.readValue(body, type);
            if (value == null) {
                throw new InvalidRequestException();
            }
            return value;
        } catch (JacksonException malformed) {
            throw new InvalidRequestException();
        }
    }
}
