package com.jack.bytejudge.problem.admin.interfaces.http;

import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.interfaces.http.dto.CreateProblemRequest;
import com.jack.bytejudge.problem.admin.interfaces.http.dto.SaveDraftRequest;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class StrictDraftJsonTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void titleOnlyCreateRejectsUnknownDuplicateTrailingAndCoercedFields() {
        assertThat(read("{\"title\":\"题目\"}", CreateProblemRequest.class).title()).isEqualTo("题目");
        for (String invalid : new String[]{"null", "{}", "{\"title\":1}",
                "{\"title\":\"a\",\"role\":\"admin\"}", "{\"title\":\"a\",\"title\":\"b\"}",
                "{\"title\":\"a\"} {}"}) {
            assertThatThrownBy(() -> read(invalid, CreateProblemRequest.class))
                    .isInstanceOf(DraftValidationException.class);
        }
    }

    @Test
    void fullSaveAllowsExplicitNullValuesButRejectsOmittedFields() {
        Map<String, Object> fields = fields();
        assertThat(read(json.writeValueAsString(fields), SaveDraftRequest.class).statement()).isNull();
        fields.remove("statement");
        assertThatThrownBy(() -> read(json.writeValueAsString(fields), SaveDraftRequest.class))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void revisionMustRemainAnIntegerWithoutScalarCoercion() {
        for (Object revision : new Object[]{"0", 0.5, true}) {
            Map<String, Object> fields = fields();
            fields.put("expectedRevision", revision);
            assertThatThrownBy(() -> read(json.writeValueAsString(fields), SaveDraftRequest.class))
                    .isInstanceOf(DraftValidationException.class);
        }
    }

    private Map<String, Object> fields() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", "title");
        for (String key : new String[]{"statement", "inputDescription", "outputDescription", "difficulty",
                "tags", "timeLimitCpuMs", "timeLimitWallMs", "memoryLimitMiB"}) fields.put(key, null);
        fields.put("expectedRevision", 0);
        return fields;
    }

    private <T> T read(String body, Class<T> type) {
        return StrictDraftJson.read(body.getBytes(StandardCharsets.UTF_8), type);
    }
}
