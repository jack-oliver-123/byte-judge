package com.jack.bytejudge.user.auth;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real gateway-to-problem-service HTTP acceptance for specification 002 (draft lifecycle and test
 * data registration). The user fixture owns disposable MySQL/Redis/Nacos and starts a real problem
 * JVM from its executable JAR with its own schema and account; no problem classes are linked here.
 */
class ProblemDraftFlowTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static AuthenticationFlowTest.AuthenticationSystem system;

    @BeforeAll
    static void startSystem() throws Exception {
        system = new AuthenticationFlowTest.AuthenticationSystem(Map.of(), 1, true);
        system.start();
    }

    @AfterAll
    static void stopSystem() throws Exception {
        if (system != null) {
            system.close();
        }
    }

    // ------------------------------------------------------------------ create

    @Test
    void administratorCreatesDraftWithTrimmedUnicodeTitleAndPermanentNumber() throws Exception {
        var admin = administrator();

        var first = createDraft(admin, "  两数之和 😀  ");
        JsonNode firstData = json(first).path("data");
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(json(first).path("code").asInt(-1)).isZero();
        assertThat(first.headers().firstValue("Cache-Control")).as("management responses are not cacheable")
                .hasValueSatisfying(value -> assertThat(value).contains("no-store"));
        assertThat(firstData.path("id").asText()).matches("\\d+");
        assertThat(firstData.path("draftId").asText()).matches("\\d+");
        assertThat(firstData.path("problemNo").asInt()).isGreaterThanOrEqualTo(1000);
        assertThat(firstData.path("revision").asInt()).isZero();

        long problemId = Long.parseLong(firstData.path("id").asText());
        JsonNode detail = getDetail(admin, problemId);
        assertThat(detail.path("id").asText()).isEqualTo(Long.toString(problemId));
        assertThat(detail.path("problemNo").asInt()).isEqualTo(firstData.path("problemNo").asInt());
        assertThat(detail.path("status").asText()).isEqualTo("DRAFT");
        assertThat(detail.path("everPublished").asBoolean()).isFalse();
        assertThat(detail.path("draftId").asText()).isEqualTo(firstData.path("draftId").asText());
        assertThat(detail.path("revision").asInt()).isZero();
        assertThat(detail.path("title").asText()).isEqualTo("两数之和 😀");
        assertThat(detail.hasNonNull("statement") ? detail.path("statement").asText() : "")
                .as("a create-only title leaves the editable content empty").isEmpty();
        assertThat(detail.path("difficulty").isNull()).isTrue();
        assertThat(detail.path("tags").isEmpty()).isTrue();
        assertThat(detail.toString()).doesNotContain("artifact-root").doesNotContain("bj_problem");

        var second = createDraft(admin, "第二题");
        int secondNo = json(second).path("data").path("problemNo").asInt();
        assertThat(secondNo).as("problem numbers are monotonic and unique").isGreaterThan(
                firstData.path("problemNo").asInt());

        // Exactly 100 Unicode code points is accepted; 101 is not.
        var hundred = createDraft(admin, "题".repeat(100));
        assertThat(hundred.statusCode()).as(hundred.body()).isEqualTo(200);
        long hundredId = Long.parseLong(json(hundred).path("data").path("id").asText());
        assertThat(getDetail(admin, hundredId).path("title").asText()).isEqualTo("题".repeat(100));

        assertInvalidCreate(admin, Map.of("title", ""));
        assertInvalidCreate(admin, Map.of("title", " \t\n "));
        assertInvalidCreate(admin, Map.of("title", "题".repeat(101)));
        assertInvalidCreate(admin, Map.of());
        assertInvalidCreate(admin, Map.of("title", List.of("x")));
    }

    // ------------------------------------------------------------------- save

    @Test
    void casSaveIncrementsRevisionEvenWithoutChangesAndConflictEchoesCurrentDraft() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "并发保存");
        long draftId = draftId(admin, problemId);
        assertThat(draftId).isPositive();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", "  标题去空白  ");
        fields.put("statement", "题面 statement");
        fields.put("inputDescription", "输入说明");
        fields.put("outputDescription", "输出说明");
        fields.put("difficulty", "MEDIUM");
        fields.put("tags", List.of("数组", "哈希表"));
        fields.put("timeLimitCpuMs", 1000);
        fields.put("timeLimitWallMs", 2000);
        fields.put("memoryLimitMiB", 256);

        var saved = saveDraft(admin, problemId, 0, fields);
        assertThat(saved.statusCode()).as(saved.body()).isEqualTo(200);
        JsonNode savedData = json(saved).path("data");
        assertThat(savedData.path("revision").asInt()).isEqualTo(1);
        assertThat(savedData.path("title").asText()).isEqualTo("标题去空白");
        assertThat(savedData.path("statement").asText()).isEqualTo("题面 statement");
        assertThat(savedData.path("tags").toString()).contains("数组", "哈希表");

        JsonNode detail = getDetail(admin, problemId);
        assertThat(detail.path("revision").asInt()).isEqualTo(1);
        assertThat(detail.path("title").asText()).isEqualTo("标题去空白");
        assertThat(detail.path("difficulty").asText()).isEqualTo("MEDIUM");
        assertThat(detail.path("timeLimitCpuMs").asInt()).isEqualTo(1000);
        assertThat(detail.path("timeLimitWallMs").asInt()).isEqualTo(2000);
        assertThat(detail.path("memoryLimitMiB").asInt()).isEqualTo(256);

        var unchanged = saveDraft(admin, problemId, 1, fields);
        assertThat(unchanged.statusCode()).as(unchanged.body()).isEqualTo(200);
        assertThat(json(unchanged).path("data").path("revision").asInt())
                .as("an accepted save advances the revision even when values are unchanged").isEqualTo(2);
        assertThat(getDetail(admin, problemId).path("revision").asInt()).isEqualTo(2);

        var stale = saveDraft(admin, problemId, 1, fields);
        assertThat(stale.statusCode()).as("stale CAS is a conflict").isEqualTo(409);
        assertThat(json(stale).path("code").asInt(-1)).isEqualTo(40910);
        JsonNode conflictData = json(stale).path("data");
        assertThat(conflictData.path("revision").asInt())
                .as("the conflict must expose the current revision").isEqualTo(2);
        assertThat(conflictData.path("title").asText()).isEqualTo("标题去空白");
        assertThat(conflictData.path("statement").asText()).isEqualTo("题面 statement");
        assertThat(getDetail(admin, problemId).path("revision").asInt())
                .as("a rejected save does not advance the revision").isEqualTo(2);

        var missing = system.problemCall(admin.browser(), "PATCH",
                "/api/v1/admin/problems/99999999/draft", csrf(admin), patchBody(0, fields),
                "application/json");
        assertThat(missing.statusCode()).as(missing.body()).isEqualTo(404);
        assertThat(json(missing).path("code").asInt(-1)).isEqualTo(40400);

        var missingRevision = system.problemCall(admin.browser(), "PATCH",
                "/api/v1/admin/problems/" + problemId + "/draft", csrf(admin),
                JSON.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(missingRevision.statusCode()).as(missingRevision.body()).isEqualTo(400);
        assertThat(json(missingRevision).path("code").asInt(-1)).isEqualTo(40000);
    }

    @Test
    void draftJsonBoundaryRejectsOmittedUnknownDuplicateTrailingAndCoercedFields() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "JSON边界");
        Map<String, Object> valid = fieldsWithDifficulty("JSON 边界", "EASY");

        // Strict request parsing: the complete editable keyset is mandatory.
        var omitted = new LinkedHashMap<>(valid);
        omitted.remove("statement");
        assertInvalidPatch(admin, problemId, patchBody(0, omitted));

        var unknown = new LinkedHashMap<>(valid);
        unknown.put("unexpectedField", 1);
        assertInvalidPatch(admin, problemId, patchBody(0, unknown));

        String validJson = new String(patchBody(0, valid), StandardCharsets.UTF_8);
        // Duplicate keys, trailing content and JSON type coercion must never be silently accepted.
        assertInvalidPatch(admin, problemId,
                (validJson.substring(0, validJson.length() - 1) + ",\"title\":\"duplicate\"}")
                        .getBytes(StandardCharsets.UTF_8));
        assertInvalidPatch(admin, problemId, (validJson + "{}").getBytes(StandardCharsets.UTF_8));
        assertInvalidPatch(admin, problemId,
                validJson.replaceFirst("\"title\":\"[^\"]*\"", "\"title\":123")
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(getDetail(admin, problemId).path("revision").asInt())
                .as("rejected protocol requests never advance the draft").isZero();
        var accepted = saveDraft(admin, problemId, 0, valid);
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(json(accepted).path("data").path("revision").asInt()).isEqualTo(1);
    }

    // ----------------------------------------------------------------- limits

    @Test
    void draftStageEnforcesUtf8ContentTagsAndResourceBoundaries() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "边界校验");
        AtomicRevision revision = new AtomicRevision();

        // Exactly 100 KiB of UTF-8 is accepted; one more byte is rejected. Chinese code points
        // account for 3 bytes each, so this proves the limit is bytes, not characters.
        String exactUtf8 = "题".repeat((100 * 1024 - 1) / 3) + "a";
        assertThat(exactUtf8.getBytes(StandardCharsets.UTF_8)).hasSize(100 * 1024);
        var accepted = saveDraft(admin, problemId, revision.value(), fieldsWithStatement(exactUtf8));
        assertThat(accepted.statusCode()).as("100 KiB boundary must be accepted: %s", accepted.body())
                .isEqualTo(200);
        revision.advance(json(accepted).path("data").path("revision").asInt());

        var tooLarge = saveDraft(admin, problemId, revision.value(),
                fieldsWithStatement(exactUtf8 + "a"));
        assertThat(tooLarge.statusCode()).as(tooLarge.body()).isEqualTo(400);
        assertThat(json(tooLarge).path("code").asInt(-1)).isEqualTo(40000);
        assertThat(getDetail(admin, problemId).path("revision").asInt())
                .as("a rejected content limit never advances the revision").isEqualTo(revision.value());

        // Tags: 5 accepted, 6 rejected; 20 code points accepted, 21 rejected; blank rejected.
        var fiveTags = saveDraft(admin, problemId, revision.value(), fieldsWithTags(List.of(
                "一二三四五六七八九十一二三四五六七八九十", "b", "c", "d", "e")));
        assertThat(fiveTags.statusCode()).as(fiveTags.body()).isEqualTo(200);
        revision.advance(json(fiveTags).path("data").path("revision").asInt());
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithTags(List.of(
                "a", "b", "c", "d", "e", "f")));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithTags(List.of("题".repeat(21))));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithTags(List.of("  ")));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithTags(List.of("x".repeat(20) + "a")));

        // CPU 100..10,000; wall 100..30,000 and never below CPU; memory 64..2,048 MiB.
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(99, 100, 64));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(10_001, 10_001, 64));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(100, 99, 64));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(1_000, 999, 64));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(100, 100, 63));
        assertInvalidSave(admin, problemId, revision.value(), fieldsWithLimits(100, 100, 2_049));
        var lowerBounds = saveDraft(admin, problemId, revision.value(), fieldsWithLimits(100, 100, 64));
        assertThat(lowerBounds.statusCode()).as(lowerBounds.body()).isEqualTo(200);
        revision.advance(json(lowerBounds).path("data").path("revision").asInt());
        var upperBounds = saveDraft(admin, problemId, revision.value(), fieldsWithLimits(10_000, 30_000, 2_048));
        assertThat(upperBounds.statusCode()).as(upperBounds.body()).isEqualTo(200);

        // All three optional limits may be absent together.
        long otherProblem = createDraftId(admin, "无限制");
        var nullLimits = saveDraft(admin, otherProblem, 0, fieldsWithLimits(null, null, null));
        assertThat(nullLimits.statusCode()).as(nullLimits.body()).isEqualTo(200);
    }

    // ------------------------------------------------------------------- list

    @Test
    void backendListFiltersByTitleAndDifficultyPagesAndHidesDeletedByDefault() throws Exception {
        var admin = administrator();
        String token = "列表-" + UUID.randomUUID().toString().substring(0, 8);
        long easy = createDraftId(admin, token + "-简单");
        long medium = createDraftId(admin, token + "-中等");
        long plain = createDraftId(admin, token + "-无难度");

        saveDraftAccepted(admin, easy, 0, fieldsWithDifficulty(token + "-简单", "EASY"));
        saveDraftAccepted(admin, medium, 0, fieldsWithDifficulty(token + "-中等", "MEDIUM"));

        JsonNode byTitle = list(admin, "/api/v1/admin/problems?title=" + url(token)
                + "&pageNum=1&pageSize=10");
        assertThat(byTitle.path("total").asInt()).isEqualTo(3);
        assertThat(listItems(byTitle).size()).isEqualTo(3);
        var listedNumbers = new ArrayList<Integer>();
        for (JsonNode item : listItems(byTitle)) {
            listedNumbers.add(item.path("problemNo").asInt());
        }
        assertThat(listedNumbers).as("creation-time descending")
                .isSortedAccordingTo((left, right) -> Integer.compare(right, left));
        assertThat(listItems(byTitle).get(0).has("statement"))
                .as("the list projection never exposes draft content").isFalse();

        JsonNode easyOnly = list(admin, "/api/v1/admin/problems?title=" + url(token)
                + "&difficulty=EASY&pageNum=1&pageSize=10");
        assertThat(listItems(easyOnly).size()).isEqualTo(1);
        assertThat(listItems(easyOnly).get(0).path("problemNo").asInt()).isEqualTo(
                getDetail(admin, easy).path("problemNo").asInt());
        assertThat(listItems(easyOnly).get(0).path("difficulty").asText()).isEqualTo("EASY");

        JsonNode mediumOnly = list(admin, "/api/v1/admin/problems?title=" + url(token)
                + "&difficulty=MEDIUM&pageNum=1&pageSize=10");
        assertThat(listItems(mediumOnly).size()).isEqualTo(1);
        assertThat(listItems(mediumOnly).get(0).path("problemNo").asInt()).isEqualTo(
                getDetail(admin, medium).path("problemNo").asInt());

        for (int pageSize : List.of(10, 20, 50)) {
            var accepted = system.problemCall(admin.browser(), "GET",
                    "/api/v1/admin/problems?title=" + url(token) + "&pageNum=1&pageSize=" + pageSize,
                    Map.of(), null, null);
            assertThat(accepted.statusCode()).as("pageSize %s: %s", pageSize, accepted.body()).isEqualTo(200);
        }
        assertInvalidList(admin, "/api/v1/admin/problems?pageNum=0");
        assertInvalidList(admin, "/api/v1/admin/problems?pageNum=-1");
        assertInvalidList(admin, "/api/v1/admin/problems?pageSize=15");
        assertInvalidList(admin, "/api/v1/admin/problems?pageSize=abc");

        var deleted = deleteDraft(admin, plain, 0);
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(200);
        JsonNode afterDelete = list(admin, "/api/v1/admin/problems?title=" + url(token)
                + "&pageNum=1&pageSize=10");
        assertThat(afterDelete.path("total").asInt()).as("DELETED drafts are hidden by default").isEqualTo(2);
        var visibleNumbers = new ArrayList<Integer>();
        for (JsonNode item : listItems(afterDelete)) {
            visibleNumbers.add(item.path("problemNo").asInt());
        }
        assertThat(visibleNumbers).doesNotContain(json(deleted).path("data").path("problemNo").asInt());

        JsonNode explicitDeleted = list(admin, "/api/v1/admin/problems?title=" + url(token)
                + "&includeDeleted=true&pageNum=1&pageSize=10");
        assertThat(explicitDeleted.path("total").asInt())
                .as("an explicit query may still show deleted drafts").isEqualTo(3);
    }

    // ----------------------------------------------------------------- delete

    @Test
    void deletionIsLogicalKeepsHistoryAndNeverRecyclesTheProblemNumber() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "待删除题目");
        int number = getDetail(admin, problemId).path("problemNo").asInt();

        var stale = deleteDraft(admin, problemId, 1);
        assertThat(stale.statusCode()).as(stale.body()).isEqualTo(409);
        assertThat(json(stale).path("code").asInt(-1)).isEqualTo(40910);

        var deleted = deleteDraft(admin, problemId, 0);
        assertThat(deleted.statusCode()).as(deleted.body()).isEqualTo(200);

        var direct = system.problemCall(admin.browser(), "GET",
                "/api/v1/admin/problems/" + problemId, Map.of(), null, null);
        assertThat(direct.statusCode()).as("a deleted draft is not retrievable").isEqualTo(404);
        assertThat(json(direct).path("code").asInt(-1)).isEqualTo(40400);

        var repeated = deleteDraft(admin, problemId, 0);
        assertThat(repeated.statusCode()).as("repeating the same deletion is idempotent")
                .isEqualTo(200);
        assertThat(json(repeated).path("data").path("status").asText()).isEqualTo("DELETED");
        assertThat(json(repeated).path("data").path("problemNo").asInt()).isEqualTo(number);
        assertThat(json(repeated).path("data").path("id").asText()).isEqualTo(Long.toString(problemId));

        int before = getDetail(admin, createDraftId(admin, "编号参照")).path("problemNo").asInt();
        assertThat(before).as("the deleted number is permanently occupied").isGreaterThan(number);
        long next = createDraftId(admin, "新题目");
        assertThat(getDetail(admin, next).path("problemNo").asInt())
                .as("allocated numbers strictly increase and never reuse a deleted one").isGreaterThan(before);
    }

    // -------------------------------------------------------------- artifacts

    @Test
    void artifactUploadRegistersDigestEnforcesLimitsAndDeduplicatesByContent() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "测试数据");
        saveDraftAccepted(admin, problemId, 0, fieldsWithDifficulty("测试数据", "EASY"));
        int revision = getDetail(admin, problemId).path("revision").asInt();

        byte[] content = "样例输入\r\n1 2\r\n".getBytes(StandardCharsets.UTF_8);
        var first = upload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, "样例 输入.txt", content);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        JsonNode receipt = json(first).path("data");
        assertThat(receipt.path("artifactId").asText()).matches("\\d+");
        assertThat(receipt.path("contentDigest").asText()).isEqualTo(sha256(content));
        assertThat(receipt.path("byteSize").asLong()).isEqualTo(content.length);
        assertThat(receipt.path("fileName").asText()).isEqualTo("样例 输入.txt");
        assertThat(receipt.path("kind").asText()).isEqualTo("SAMPLE_INPUT");
        assertThat(receipt.path("caseOrder").asInt()).isEqualTo(1);
        assertThat(receipt.path("displayOrder").asInt()).isZero();
        assertThat(first.body()).as("no filesystem path or content may leak")
                .doesNotContain(system.artifactRoot().toString().replace('\\', '/'))
                .doesNotContain("样例输入\r\n1 2");
        assertThat(getDetail(admin, problemId).path("revision").asInt())
                .as("an accepted upload advances the draft revision").isEqualTo(revision + 1);
        revision += 1;
        JsonNode afterUpload = getDetail(admin, problemId);
        assertThat(afterUpload.path("artifacts").size()).isEqualTo(1);
        assertThat(afterUpload.path("artifacts").get(0).path("contentDigest").asText())
                .isEqualTo(sha256(content));
        assertThat(afterUpload.toString()).as("detail exposes manifest metadata but no file bytes")
                .doesNotContain("样例输入\r\n1 2");
        assertThat(system.artifactRowCount(problemId)).isEqualTo(1);

        // Content-addressed dedup: identical bytes in a different slot are a second manifest entry
        // with the same digest and a distinct artifact id.
        var duplicate = upload(admin, problemId, revision, "HIDDEN_INPUT", 2, 1, "hidden.txt", content);
        assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(200);
        JsonNode duplicateData = json(duplicate).path("data");
        assertThat(duplicateData.path("contentDigest").asText()).isEqualTo(sha256(content));
        assertThat(duplicateData.path("artifactId").asText())
                .isNotEqualTo(receipt.path("artifactId").asText());
        revision += 1;
        assertThat(system.artifactRowCount(problemId)).isEqualTo(2);

        // The same live slot is never silently replaced.
        var occupied = upload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, "again.txt", content);
        assertThat(occupied.statusCode()).as(occupied.body()).isEqualTo(400);
        assertThat(json(occupied).path("code").asInt(-1)).isEqualTo(40000);
        assertThat(system.artifactRowCount(problemId)).isEqualTo(2);

        // Empty content is valid (SHA-256 of no bytes); missing content is not.
        var empty = upload(admin, problemId, revision, "SAMPLE_OUTPUT", 1, 0, "empty.txt", new byte[0]);
        assertThat(empty.statusCode()).as("empty input/output is allowed: %s", empty.body()).isEqualTo(200);
        assertThat(json(empty).path("data").path("contentDigest").asText())
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(json(empty).path("data").path("byteSize").asLong()).isZero();
        revision += 1;

        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, "late.txt", content);
        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, "bad.txt", content,
                Map.of("kind", "样例输入"));
        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 0, 0, "bad.txt", content);
        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 21, 0, "bad.txt", content);
        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 1, -1, "bad.txt", content);
        assertInvalidUpload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, null, null);

        // 1 MiB exactly is accepted; one more byte is rejected.
        byte[] max = new byte[1_048_576];
        java.util.Arrays.fill(max, (byte) 'a');
        var maxAccepted = upload(admin, problemId, revision, "HIDDEN_OUTPUT", 3, 2, "max.out", max);
        assertThat(maxAccepted.statusCode()).as("1 MiB exactly: %s", maxAccepted.body()).isEqualTo(200);
        assertThat(json(maxAccepted).path("data").path("byteSize").asLong()).isEqualTo(1_048_576);
        revision += 1;
        byte[] tooLarge = java.util.Arrays.copyOf(max, 1_048_577);
        var oversize = upload(admin, problemId, revision, "HIDDEN_OUTPUT", 4, 3, "too-large.out", tooLarge);
        assertThat(oversize.statusCode()).as(oversize.body()).isEqualTo(400);
        assertThat(json(oversize).path("code").asInt(-1)).isEqualTo(40000);
        assertThat(system.artifactRowCount(problemId))
                .as("a rejected upload leaves no manifest").isEqualTo(4);

        var missingProblem = upload(admin, 99_999_999L, 0, "SAMPLE_INPUT", 1, 0, "x.txt", content);
        assertThat(missingProblem.statusCode()).as(missingProblem.body()).isEqualTo(404);
        assertThat(json(missingProblem).path("code").asInt(-1)).isEqualTo(40400);
    }

    @Test
    void artifactGroupLimitIsTwentyAndCompletingAnExistingCounterpartRemainsAllowed() throws Exception {
        var admin = administrator();
        long problemId = createDraftId(admin, "用例组上限");
        int revision = getDetail(admin, problemId).path("revision").asInt();

        // Ten sample groups and ten hidden groups occupy the full 20-group budget.
        for (int order = 1; order <= 10; order++) {
            var sample = upload(admin, problemId, revision, "SAMPLE_INPUT", order, 0,
                    "sample-" + order + ".in", new byte[]{(byte) order});
            assertThat(sample.statusCode()).as(sample.body()).isEqualTo(200);
            revision += 1;
            var hidden = upload(admin, problemId, revision, "HIDDEN_INPUT", order, 0,
                    "hidden-" + order + ".in", new byte[]{(byte) (order + 10)});
            assertThat(hidden.statusCode()).as(hidden.body()).isEqualTo(200);
            revision += 1;
        }

        var overCap = upload(admin, problemId, revision, "SAMPLE_INPUT", 11, 0, "over.in", new byte[]{21});
        assertThat(overCap.statusCode()).as(overCap.body()).isEqualTo(400);
        assertThat(json(overCap).path("code").asInt(-1)).isEqualTo(40000);

        // The 20-group budget is unchanged; filling the other half of an existing group is not a
        // new group and must still be accepted.
        var counterpart = upload(admin, problemId, revision, "SAMPLE_OUTPUT", 1, 0,
                "sample-1.out", new byte[]{42});
        assertThat(counterpart.statusCode()).as(counterpart.body()).isEqualTo(200);
        assertThat(json(counterpart).path("data").path("kind").asText()).isEqualTo("SAMPLE_OUTPUT");
    }

    // ------------------------------------------------------------- permission

    @Test
    void guestOrdinaryUserForgedHeadersAndDirectCallsCannotReachAdminApi() throws Exception {
        // Guest through the gateway: refused before any downstream work, so do not wait for a
        // problem-service access line.
        var guest = system.newBrowser();
        var guestList = system.problemCallNoWait(guest, "GET", "/api/v1/admin/problems",
                Map.of(), null, null);
        assertThat(guestList.statusCode()).isEqualTo(401);
        assertThat(json(guestList).path("code").asInt(-1)).isEqualTo(40100);
        var guestCreate = system.problemCallNoWait(guest, "POST", "/api/v1/admin/problems",
                Map.of("Content-Type", "application/json"),
                "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(guestCreate.statusCode()).isEqualTo(401);
        assertThat(json(guestCreate).path("code").asInt(-1)).isEqualTo(40100);

        // Forged identity/internal headers never authenticate a guest at the gateway.
        var forged = new LinkedHashMap<String, String>();
        forged.put("X-User-Id", "1");
        forged.put("X-User-Role", "admin");
        forged.put("X-Internal-Service", "gateway");
        forged.put("X-Internal-Credential", system.problemCredential());
        var forgedList = system.problemCallNoWait(guest, "GET", "/api/v1/admin/problems",
                forged, null, null);
        assertThat(forgedList.statusCode()).as("forged identity headers are stripped").isEqualTo(401);
        assertThat(json(forgedList).path("code").asInt(-1)).isEqualTo(40100);

        // Ordinary user is authenticated but not authorized. The gateway refuses it.
        var ordinaryBrowser = system.newBrowser();
        assertThat(system.login(ordinaryBrowser, system.csrf(ordinaryBrowser), system.ordinaryUser)
                .response().statusCode()).isEqualTo(200);
        var ordinaryCsrf = system.csrf(ordinaryBrowser);
        var ordinaryHeaders = new LinkedHashMap<String, String>();
        ordinaryHeaders.put(ordinaryCsrf.headerName(), ordinaryCsrf.token());
        ordinaryHeaders.put("X-User-Role", "admin");
        var ordinaryList = system.problemCallNoWait(ordinaryBrowser, "GET", "/api/v1/admin/problems",
                ordinaryHeaders, null, null);
        assertThat(ordinaryList.statusCode()).isEqualTo(403);
        assertThat(json(ordinaryList).path("code").asInt(-1)).isEqualTo(40300);
        var ordinaryCreate = system.problemCallNoWait(ordinaryBrowser, "POST", "/api/v1/admin/problems",
                ordinaryHeaders, "{\"title\":\"x\"}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(ordinaryCreate.statusCode()).isEqualTo(403);
        assertThat(json(ordinaryCreate).path("code").asInt(-1)).isEqualTo(40300);

        // CSRF is required for every management write.
        var admin = administrator();
        var noCsrf = system.problemCall(admin.browser(), "POST", "/api/v1/admin/problems",
                Map.of("Content-Type", "application/json"),
                "{\"title\":\"csrf\"}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(noCsrf.statusCode()).as(noCsrf.body()).isEqualTo(403);
        assertThat(json(noCsrf).path("code").asInt(-1)).isEqualTo(40301);
        var wrongCsrf = system.problemCall(admin.browser(), "POST", "/api/v1/admin/problems",
                Map.of("Content-Type", "application/json", admin.headerName(), "invalid-token"),
                "{\"title\":\"csrf\"}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(wrongCsrf.statusCode()).as(wrongCsrf.body()).isEqualTo(403);
        assertThat(json(wrongCsrf).path("code").asInt(-1)).isEqualTo(40301);

        // Direct service access bypassing the gateway executes the same authentication.
        var directGuest = system.problemDirectCall(system.newBrowser(), "GET",
                "/api/v1/admin/problems", forged, null, null);
        assertThat(directGuest.statusCode()).as("direct service calls reject guest/forged identity")
                .isEqualTo(401);
        assertThat(json(directGuest).path("code").asInt(-1)).isEqualTo(40100);
        var directOrdinary = system.problemDirectCall(ordinaryBrowser, "GET",
                "/api/v1/admin/problems", Map.of(), null, null);
        assertThat(directOrdinary.statusCode()).isEqualTo(403);
        assertThat(json(directOrdinary).path("code").asInt(-1)).isEqualTo(40300);
        var directAdmin = system.problemDirectCall(admin.browser(), "GET",
                "/api/v1/admin/problems", Map.of(), null, null);
        assertThat(directAdmin.statusCode()).as(directAdmin.body()).isEqualTo(200);
    }

    @Test
    void liveAccountStatusAndEpochChangesRevokeExistingProblemAccess() throws Exception {
        var fixtureAdmin = system.uniqueFixtureUser("problem_admin", "admin", "normal");
        var browser = system.newBrowser();
        assertThat(system.login(browser, system.csrf(browser), fixtureAdmin).response().statusCode())
                .isEqualTo(200);
        var csrf = system.csrf(browser);
        var headers = Map.of(csrf.headerName(), csrf.token());
        var healthy = system.problemCall(browser, "GET", "/api/v1/admin/problems", headers, null, null);
        assertThat(healthy.statusCode()).as(healthy.body()).isEqualTo(200);

        try {
            system.bumpAuthEpoch(fixtureAdmin.account());
            var afterEpoch = system.problemCallNoWait(browser, "GET", "/api/v1/admin/problems",
                    headers, null, null);
            assertThat(afterEpoch.statusCode())
                    .as("an authentication epoch change invalidates the shared session").isEqualTo(401);
            assertThat(json(afterEpoch).path("code").asInt(-1)).isEqualTo(40100);

            // A fresh login adopts the new epoch, then disabling the account revokes it live.
            assertThat(system.login(browser, system.csrf(browser), fixtureAdmin).response().statusCode())
                    .isEqualTo(200);
            var freshCsrf = system.csrf(browser);
            var freshHeaders = Map.of(freshCsrf.headerName(), freshCsrf.token());
            assertThat(system.problemCall(browser, "GET", "/api/v1/admin/problems",
                    freshHeaders, null, null).statusCode()).isEqualTo(200);

            system.setStatus(fixtureAdmin.account(), "disabled");
            var afterDisable = system.problemCallNoWait(browser, "GET", "/api/v1/admin/problems",
                    freshHeaders, null, null);
            assertThat(afterDisable.statusCode()).as("a disabled account is refused on the next request")
                    .isEqualTo(401);
            assertThat(json(afterDisable).path("code").asInt(-1)).isEqualTo(40100);
        } finally {
            system.setStatus(fixtureAdmin.account(), "normal");
        }
    }

    // ------------------------------------------------------------------ audit

    @Test
    void managementWritesAreAuditedAndLogsNeverCarryContentOrCredentials() throws Exception {
        var admin = administrator();
        String marker = "审计题面-" + UUID.randomUUID();
        long problemId = createDraftId(admin, "审计题目");
        saveDraftAccepted(admin, problemId, 0, fieldsWith("statement", marker));

        byte[] content = ("artifact-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        int revision = getDetail(admin, problemId).path("revision").asInt();
        assertThat(upload(admin, problemId, revision, "SAMPLE_INPUT", 1, 0, "audit.txt", content)
                .statusCode()).isEqualTo(200);
        revision += 1;
        // An invalid kind reaches the use case, so its rejection is auditable (container-level
        // multipart limits can abort before application code runs).
        var rejected = upload(admin, problemId, revision, "NOT_A_KIND", 2, 0, "audit2.txt", content);
        assertThat(rejected.statusCode()).isEqualTo(400);

        long problemNo = getDetail(admin, problemId).path("problemNo").asLong();
        var deleteResponse = deleteDraft(admin, problemId, revision);
        assertThat(deleteResponse.statusCode()).isEqualTo(200);
        JsonNode deletedData = json(deleteResponse).path("data");
        assertThat(deletedData.path("status").asText()).isEqualTo("DELETED");

        assertThat(system.problemAuditCount("PROBLEM_CREATE", "SUCCESS", problemId)).isEqualTo(1);
        assertThat(system.problemAuditCount("PROBLEM_UPDATE", "SUCCESS", problemId)).isEqualTo(1);
        assertThat(system.problemAuditCount("ARTIFACT_UPLOAD", "SUCCESS", problemId)).isEqualTo(1);
        assertThat(system.problemAuditCount("ARTIFACT_UPLOAD", "REJECTED", problemId)).isEqualTo(1);
        assertThat(system.problemAuditCount("PROBLEM_DELETE", "SUCCESS", problemId)).isEqualTo(1);
        String audit = system.problemAuditDump();
        assertThat(audit).contains(system.administrator.account())
                .as("audit rows never carry statement or file content")
                .doesNotContain(marker, new String(content, StandardCharsets.UTF_8));

        String problemLog = java.nio.file.Files.readString(system.problemApplicationLog(),
                StandardCharsets.UTF_8);
        assertThat(problemLog).as("application logs never carry statement or file content")
                .doesNotContain(marker, new String(content, StandardCharsets.UTF_8));
        assertThat(problemLog).as("application logs never carry credentials or session material")
                .doesNotContain(system.administrator.password(), system.problemCredential(),
                        system.ordinaryUser.password());
        System.out.println("PROBLEM_ACCEPTANCE_AUDIT problemNo=" + problemNo + " auditRows=" + audit.lines().count());
    }

    // --------------------------------------------------------------- helpers

    private static final class AtomicRevision {
        private int value;

        int value() {
            return value;
        }

        void advance(int next) {
            assertThat(next).isGreaterThan(value);
            value = next;
        }
    }

    private record AdminSession(AuthenticationFlowTest.AuthenticationSystem.Browser browser,
                                String headerName, String token) { }

    private static AdminSession administrator() throws Exception {
        var browser = system.newBrowser();
        var login = system.login(browser, system.csrf(browser), system.administrator);
        assertThat(login.response().statusCode()).as(login.response().body()).isEqualTo(200);
        var csrf = system.csrf(browser);
        return new AdminSession(browser, csrf.headerName(), csrf.token());
    }

    private static Map<String, String> csrf(AdminSession admin) {
        return Map.of(admin.headerName(), admin.token());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static HttpResponse<String> create(AdminSession admin, String title) throws Exception {
        var body = JSON.writeValueAsString(Map.of("title", title)).getBytes(StandardCharsets.UTF_8);
        return system.problemCall(admin.browser(), "POST", "/api/v1/admin/problems",
                jsonHeaders(admin), body, "application/json");
    }

    private static HttpResponse<String> createDraft(AdminSession admin, String title) throws Exception {
        var response = create(admin, title);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json(response).path("code").asInt(-1)).isZero();
        return response;
    }

    private static long createDraftId(AdminSession admin, String title) throws Exception {
        return Long.parseLong(json(createDraft(admin, title)).path("data").path("id").asText());
    }

    private static long draftId(AdminSession admin, long problemId) throws Exception {
        return Long.parseLong(getDetail(admin, problemId).path("draftId").asText());
    }

    private static void assertInvalidCreate(AdminSession admin, Map<String, Object> body) throws Exception {
        var response = system.problemCall(admin.browser(), "POST", "/api/v1/admin/problems",
                jsonHeaders(admin), JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8),
                "application/json");
        assertThat(response.statusCode()).as("body %s: %s", body, response.body()).isEqualTo(400);
        assertThat(json(response).path("code").asInt(-1)).isEqualTo(40000);
    }

    private static JsonNode getDetail(AdminSession admin, long problemId) throws Exception {
        var response = system.problemCall(admin.browser(), "GET",
                "/api/v1/admin/problems/" + problemId, Map.of(), null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).path("data");
    }

    private static JsonNode list(AdminSession admin, String path) throws Exception {
        var response = system.problemCall(admin.browser(), "GET", path, Map.of(), null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json(response).path("code").asInt(-1)).isZero();
        return json(response).path("data");
    }

    private static JsonNode listItems(JsonNode listData) {
        return listData.path("items");
    }

    private static void assertInvalidList(AdminSession admin, String path) throws Exception {
        var response = system.problemCall(admin.browser(), "GET", path, Map.of(), null, null);
        assertThat(response.statusCode()).as(path + ": " + response.body()).isEqualTo(400);
        assertThat(json(response).path("code").asInt(-1)).isEqualTo(40000);
    }

    private static HttpResponse<String> saveDraft(AdminSession admin, long problemId, int expectedRevision,
                                                  Map<String, Object> fields) throws Exception {
        return system.problemCall(admin.browser(), "PATCH",
                "/api/v1/admin/problems/" + problemId + "/draft", csrf(admin),
                patchBody(expectedRevision, fields), "application/json");
    }

    private static byte[] patchBody(int expectedRevision, Map<String, Object> fields) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("expectedRevision", expectedRevision);
        body.putAll(fields);
        return JSON.writeValueAsBytes(body);
    }

    private static int saveDraftAccepted(AdminSession admin, long problemId, int expectedRevision,
                                        Map<String, Object> fields) throws Exception {
        var response = saveDraft(admin, problemId, expectedRevision, fields);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json(response).path("data").path("revision").asInt();
    }

    private static void assertInvalidSave(AdminSession admin, long problemId, int expectedRevision,
                                          Map<String, Object> fields) throws Exception {
        var response = saveDraft(admin, problemId, expectedRevision, fields);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json(response).path("code").asInt(-1)).isEqualTo(40000);
    }

    private static void assertInvalidPatch(AdminSession admin, long problemId, byte[] body)
            throws Exception {
        var response = system.problemCall(admin.browser(), "PATCH",
                "/api/v1/admin/problems/" + problemId + "/draft", csrf(admin), body,
                "application/json");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json(response).path("code").asInt(-1)).isEqualTo(40000);
    }

    private static Map<String, Object> editableFields() {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("statement", null);
        fields.put("inputDescription", null);
        fields.put("outputDescription", null);
        fields.put("difficulty", null);
        fields.put("tags", List.of());
        fields.put("timeLimitCpuMs", null);
        fields.put("timeLimitWallMs", null);
        fields.put("memoryLimitMiB", null);
        return fields;
    }

    private static Map<String, Object> fieldsWithStatement(String statement) {
        var fields = editableFields();
        fields.put("title", "内容边界");
        fields.put("statement", statement);
        return fields;
    }

    private static Map<String, Object> fieldsWithTags(List<String> tags) {
        var fields = editableFields();
        fields.put("title", "标签边界");
        fields.put("tags", tags);
        return fields;
    }

    private static Map<String, Object> fieldsWithLimits(Integer cpu, Integer wall, Integer memory) {
        var fields = editableFields();
        fields.put("title", "资源边界");
        fields.put("timeLimitCpuMs", cpu);
        fields.put("timeLimitWallMs", wall);
        fields.put("memoryLimitMiB", memory);
        return fields;
    }

    private static Map<String, Object> fieldsWithDifficulty(String title, String difficulty) {
        var fields = editableFields();
        fields.put("title", title);
        fields.put("difficulty", difficulty);
        return fields;
    }

    private static Map<String, Object> fieldsWith(String key, Object value) {
        var fields = editableFields();
        fields.put("title", "审计题目");
        fields.put(key, value);
        return fields;
    }

    private static HttpResponse<String> deleteDraft(AdminSession admin, long problemId, int expectedRevision)
            throws Exception {
        return system.problemCall(admin.browser(), "DELETE",
                "/api/v1/admin/problems/" + problemId + "?expectedRevision=" + expectedRevision,
                csrf(admin), null, null);
    }

    private static HttpResponse<String> upload(AdminSession admin, long problemId, int expectedRevision,
                                               String kind, int caseOrder, int displayOrder,
                                               String fileName, byte[] content) throws Exception {
        return upload(admin, problemId, expectedRevision, kind, caseOrder, displayOrder, fileName,
                content, Map.of());
    }

    private static HttpResponse<String> upload(AdminSession admin, long problemId, int expectedRevision,
                                               String kind, int caseOrder, int displayOrder,
                                               String fileName, byte[] content,
                                               Map<String, String> overrides) throws Exception {
        var fields = new LinkedHashMap<String, String>();
        fields.put("kind", overrides.getOrDefault("kind", kind));
        fields.put("caseOrder", Integer.toString(caseOrder));
        fields.put("displayOrder", Integer.toString(displayOrder));
        fields.put("expectedRevision", Integer.toString(expectedRevision));
        var multipart = system.multipart("----ProblemDraft" + UUID.randomUUID(), fields,
                fileName == null ? null : "file", fileName, content == null ? new byte[0] : content);
        return system.problemCall(admin.browser(), "POST",
                "/api/v1/admin/problems/" + problemId + "/draft/artifacts", csrf(admin),
                multipart.bytes(), multipart.contentType());
    }

    private static void assertInvalidUpload(AdminSession admin, long problemId, int expectedRevision,
                                            String kind, int caseOrder, int displayOrder,
                                            String fileName, byte[] content) throws Exception {
        assertInvalidUpload(admin, problemId, expectedRevision, kind, caseOrder, displayOrder,
                fileName, content, Map.of());
    }

    private static void assertInvalidUpload(AdminSession admin, long problemId, int expectedRevision,
                                            String kind, int caseOrder, int displayOrder,
                                            String fileName, byte[] content,
                                            Map<String, String> overrides) throws Exception {
        var response = upload(admin, problemId, expectedRevision, kind, caseOrder, displayOrder,
                fileName, content, overrides);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(json(response).path("code").asInt(-1)).isEqualTo(40000);
    }

    private static Map<String, String> jsonHeaders(AdminSession admin) {
        var headers = new LinkedHashMap<>(csrf(admin));
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
