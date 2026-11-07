package com.jack.bytejudge.problem.admin.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDetailRecord;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDraft;
import com.jack.bytejudge.problem.admin.domain.model.ProblemListItem;
import com.jack.bytejudge.problem.admin.domain.model.ProblemStatus;
import com.jack.bytejudge.problem.admin.domain.repository.ProblemDraftRepository;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemDraftMapper;
import com.jack.bytejudge.problem.admin.infrastructure.persistence.mapper.ProblemMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** MyBatis adapter for the problem/draft domain port. */
public final class MybatisProblemDraftRepository implements ProblemDraftRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> TAG_LIST = new TypeReference<>() {
    };

    private final ProblemMapper problems;
    private final ProblemDraftMapper drafts;
    private final IdentifierGenerator identifiers;

    public MybatisProblemDraftRepository(ProblemMapper problems, ProblemDraftMapper drafts,
                                         IdentifierGenerator identifiers) {
        this.problems = problems;
        this.drafts = drafts;
        this.identifiers = identifiers;
    }

    @Override
    public int allocateProblemNo() {
        Integer next = problems.lockNextProblemNo();
        if (next == null) {
            throw new IllegalStateException("problem_no_sequence is not initialized");
        }
        if (problems.advanceProblemNo() != 1) {
            throw new IllegalStateException("problem_no_sequence could not advance");
        }
        return next;
    }

    @Override
    public long insertProblem(int problemNo, long createdById) {
        long id = identifiers.nextId(problemNo).longValue();
        problems.insertProblem(id, problemNo, createdById);
        return id;
    }

    @Override
    public long insertDraft(long problemId, DraftContent content, long createdById) {
        long id = identifiers.nextId(content).longValue();
        drafts.insert(id, problemId, content, difficultyColumn(content), tagsJson(content.tags()),
                createdById);
        return id;
    }

    @Override
    public void attachDraft(long problemId, long draftId) {
        if (problems.attachDraft(problemId, draftId) != 1) {
            throw new IllegalStateException("problem draft could not be attached");
        }
    }

    @Override
    public Optional<ProblemDetailRecord> findDetail(long problemId) {
        return Optional.ofNullable(problems.findDetail(problemId)).map(this::toDetail);
    }

    @Override
    public Optional<ProblemDetailRecord> lockDetail(long problemId) {
        return Optional.ofNullable(problems.lockDetail(problemId)).map(this::toDetail);
    }

    @Override
    public boolean updateDraft(long problemId, long draftId, long expectedRevision,
                               DraftContent content, long updatedById) {
        int rows = drafts.updateContent(problemId, draftId, expectedRevision, content,
                difficultyColumn(content), tagsJson(content.tags()), updatedById);
        if (rows == 1) {
            problems.touch(problemId);
        }
        return rows == 1;
    }

    @Override
    public boolean advanceRevision(long draftId, long expectedRevision) {
        return drafts.advanceRevision(draftId, expectedRevision) == 1;
    }

    @Override
    public boolean markDeleted(long problemId, long draftId, long updatedById) {
        int problemRows = problems.markDeleted(problemId);
        int draftRows = drafts.markDeleted(problemId, draftId, updatedById);
        return problemRows == 1 && draftRows == 1;
    }

    @Override
    public List<ProblemListItem> findPage(String escapedTitle, Difficulty difficulty,
                                          boolean includeDeleted, long offset, int limit) {
        return problems.findPage(escapedTitle, difficultyValue(difficulty), includeDeleted, offset, limit)
                .stream()
                .map(this::toListItem)
                .toList();
    }

    @Override
    public long count(String escapedTitle, Difficulty difficulty, boolean includeDeleted) {
        return problems.count(escapedTitle, difficultyValue(difficulty), includeDeleted);
    }

    @Override
    public LockedDraft lockLiveDraft(long problemId) {
        ProblemMapper.LockedDraftRow row = problems.lockLiveDraft(problemId);
        return row == null ? null
                : new LockedDraft(row.draftId(), row.problemId(), row.revision(), row.createdById());
    }

    private ProblemDetailRecord toDetail(ProblemMapper.ProblemDetailRow row) {
        DraftContent content = new DraftContent(row.title(), row.statement(), row.inputDescription(),
                row.outputDescription(), parseDifficulty(row.difficulty()), parseTags(row.tags()),
                row.timeLimitCpuMs(), row.timeLimitWallMs(), row.memoryLimitMiB());
        ProblemDraft draft = new ProblemDraft(row.draftId(), row.draftProblemId(), row.revision(),
                content, row.draftCreatedById(), row.draftUpdatedById(), instant(row.draftCreatedAt()),
                instant(row.updatedAt()), row.draftDeleted());
        boolean deleted = row.problemDeleted() || row.draftDeleted()
                || ProblemStatus.DELETED.name().equals(row.status());
        return new ProblemDetailRecord(row.problemId(), row.problemNo(),
                ProblemStatus.fromColumn(row.status()), row.everPublished(), row.createdById(),
                instant(row.createdAt()), draft, deleted);
    }

    private ProblemListItem toListItem(ProblemMapper.ProblemListItemRow row) {
        return new ProblemListItem(row.problemId(), row.problemNo(), row.title(),
                parseDifficulty(row.difficulty()), ProblemStatus.fromColumn(row.status()),
                row.revision(), instant(row.createdAt()), instant(row.updatedAt()));
    }

    private static String difficultyColumn(DraftContent content) {
        return content.difficulty() == null ? null : content.difficulty().name();
    }

    private static String difficultyValue(Difficulty difficulty) {
        return difficulty == null ? null : difficulty.name();
    }

    private static Difficulty parseDifficulty(String stored) {
        return stored == null ? null : Difficulty.valueOf(stored);
    }

    private static String tagsJson(List<String> tags) {
        return JSON.writeValueAsString(tags);
    }

    private static List<String> parseTags(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.copyOf(JSON.readValue(stored, TAG_LIST));
    }

    private static java.time.Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
