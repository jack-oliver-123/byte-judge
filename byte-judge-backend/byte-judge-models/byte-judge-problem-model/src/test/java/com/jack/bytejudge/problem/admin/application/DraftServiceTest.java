package com.jack.bytejudge.problem.admin.application;

import com.jack.bytejudge.problem.admin.application.result.CreatedProblem;
import com.jack.bytejudge.problem.admin.application.result.DeletedProblem;
import com.jack.bytejudge.problem.admin.application.result.ProblemDetail;
import com.jack.bytejudge.problem.admin.application.result.ProblemPage;
import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import com.jack.bytejudge.problem.admin.domain.ProblemNotFoundException;
import com.jack.bytejudge.problem.admin.domain.model.Difficulty;
import com.jack.bytejudge.problem.admin.domain.model.DraftContent;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDetailRecord;
import com.jack.bytejudge.problem.admin.domain.model.ProblemDraft;
import com.jack.bytejudge.problem.admin.domain.model.ProblemListItem;
import com.jack.bytejudge.problem.admin.domain.model.ProblemStatus;
import com.jack.bytejudge.problem.admin.domain.repository.ProblemDraftRepository;
import com.jack.bytejudge.problem.artifact.application.ArtifactCatalog;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEvent;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftServiceTest {
    private static final Instant CREATED = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-11-07T10:05:00Z");
    private static final AdminActor ACTOR = new AdminActor(7L, "admin");

    private final FakeRepository repository = new FakeRepository();
    private final RecordingAudit audit = new RecordingAudit();
    private final DraftService service = new DraftService(repository, audit, emptyCatalog());

    @Test
    void creationAllocatesTheProblemNumberAndAuditsInTheSameUseCase() {
        CreatedProblem created = service.create("  Two Sum  ", ACTOR, "request-1");

        assertThat(created).isEqualTo(new CreatedProblem(11L, 1000, 22L, 0L));
        assertThat(repository.insertedContent.title()).isEqualTo("Two Sum");
        assertThat(repository.insertedContent.statement()).isNull();
        assertThat(repository.insertedContent.tags()).isEmpty();
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(ProblemAuditEvent.PROBLEM_CREATE);
            assertThat(entry.outcome()).isEqualTo(ProblemAuditOutcome.SUCCESS);
            assertThat(entry.operatorId()).isEqualTo(7L);
            assertThat(entry.operatorAccount()).isEqualTo("admin");
            assertThat(entry.problemId()).isEqualTo(11L);
            assertThat(entry.requestId()).isEqualTo("request-1");
        });
    }

    @Test
    void anAcceptedSaveAdvancesTheRevisionEvenWhenNothingChanged() {
        DraftContent unchanged = repository.detail.draft().content();
        ProblemDetail saved = service.save(11L, 3L, unchanged, ACTOR, "request-2");

        assertThat(saved.revision()).isEqualTo(4L);
        assertThat(saved.content()).isEqualTo(unchanged);
        assertThat(repository.updateCalls).isOne();
        assertThat(audit.entries).singleElement()
                .extracting(ProblemAuditEntry::event).isEqualTo(ProblemAuditEvent.PROBLEM_UPDATE);
    }

    @Test
    void aStaleSaveReturnsTheCurrentDraftAndDoesNotWrite() {
        DraftContent replacement = DraftContent.titleOnly("Overwrite");

        assertThatThrownBy(() -> service.save(11L, 2L, replacement, ACTOR, "request-3"))
                .isInstanceOfSatisfying(DraftVersionConflictException.class, conflict -> {
                    assertThat(conflict.current().revision()).isEqualTo(3L);
                    assertThat(conflict.current().content().title()).isEqualTo("Two Sum");
                });

        assertThat(repository.updateCalls).isZero();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void savingADeletedDraftIsNotFound() {
        repository.detail = detail(true);

        assertThatThrownBy(() -> service.save(11L, 3L, DraftContent.titleOnly("x"), ACTOR, "r"))
                .isInstanceOf(ProblemNotFoundException.class);
        assertThat(repository.updateCalls).isZero();
    }

    @Test
    void detailAuditsTheAdministratorPreview() {
        ProblemDetail detail = service.detail(11L, ACTOR, "request-4");

        assertThat(detail.revision()).isEqualTo(3L);
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(ProblemAuditEvent.ADMIN_ACCESS);
            assertThat(entry.outcome()).isEqualTo(ProblemAuditOutcome.SUCCESS);
            assertThat(entry.reason()).isEqualTo("PREVIEW");
            assertThat(entry.problemId()).isEqualTo(11L);
            assertThat(entry.requestId()).isEqualTo("request-4");
        });
    }

    @Test
    void deletionMarksTheDraftDeletedAndIsIdempotentForTheSameRevision() {
        DeletedProblem deleted = service.delete(11L, 3L, ACTOR, "request-5");

        assertThat(deleted).isEqualTo(new DeletedProblem(11L, 1000, 3L));
        assertThat(audit.entries).singleElement()
                .extracting(ProblemAuditEntry::event).isEqualTo(ProblemAuditEvent.PROBLEM_DELETE);

        DeletedProblem repeated = service.delete(11L, 3L, ACTOR, "request-6");
        assertThat(repeated).isEqualTo(deleted);
        assertThat(audit.entries).hasSize(1);
    }

    @Test
    void aDeletedDraftWithAnotherRevisionIsNotFound() {
        repository.detail = detail(true);

        assertThatThrownBy(() -> service.delete(11L, 2L, ACTOR, "r"))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    void aStaleDeleteReturnsTheCurrentDraftAndDoesNotWrite() {
        assertThatThrownBy(() -> service.delete(11L, 2L, ACTOR, "r"))
                .isInstanceOfSatisfying(DraftVersionConflictException.class,
                        conflict -> assertThat(conflict.current().revision()).isEqualTo(3L));
        assertThat(repository.deleteCalls).isZero();
        assertThat(audit.entries).isEmpty();
    }

    @Test
    void listRejectsPaginationOutsideTheFrozenChoices() {
        assertThatThrownBy(() -> service.list(null, null, false, 0, 20))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> service.list(null, null, false, 1, 15))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> service.list(null, null, false, 1, 0))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void listEscapesWildcardsAndAppliesFiltersWithAStableOffset() {
        repository.pageItems = List.of(new ProblemListItem(11L, 1000, "Two Sum",
                Difficulty.EASY, ProblemStatus.DRAFT, 3L, CREATED, UPDATED));
        repository.total = 1;

        ProblemPage page = service.list(" 50% ", "easy", true, 2, 10);

        assertThat(page.items()).hasSize(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(repository.pageTitle).isEqualTo("%50!%%");
        assertThat(repository.pageDifficulty).isEqualTo(Difficulty.EASY);
        assertThat(repository.pageIncludeDeleted).isTrue();
        assertThat(repository.pageOffset).isEqualTo(10L);
        assertThat(repository.pageLimit).isEqualTo(10);
    }

    @Test
    void artifactLockRequiresTheCurrentRevisionAndALiveDraft() {
        repository.locked = new ProblemDraftRepository.LockedDraft(22L, 11L, 3L, 7L);

        assertThat(service.lockForArtifact(11L, 3L)).isEqualTo(22L);

        assertThatThrownBy(() -> service.lockForArtifact(11L, 2L))
                .isInstanceOf(DraftArtifactSeam.RevisionConflictException.class);

        repository.locked = null;
        assertThatThrownBy(() -> service.lockForArtifact(11L, 3L))
                .isInstanceOf(DraftArtifactSeam.DraftNotFoundException.class);
    }

    @Test
    void artifactRevisionAdvanceOnlySucceedsWhenTheExpectedRevisionStillMatches() {
        repository.advanceResult = true;
        assertThat(service.advanceRevision(22L, 3L)).isTrue();
        assertThat(repository.advanceDraftId).isEqualTo(22L);
        assertThat(repository.advanceExpected).isEqualTo(3L);

        repository.advanceResult = false;
        assertThat(service.advanceRevision(22L, 2L)).isFalse();
    }

    private static ProblemDetailRecord detail(boolean deleted) {
        DraftContent content = DraftContent.titleOnly("Two Sum");
        ProblemDraft draft = new ProblemDraft(22L, 11L, 3L, content, 7L, 7L, CREATED, UPDATED,
                deleted);
        return new ProblemDetailRecord(11L, 1000,
                deleted ? ProblemStatus.DELETED : ProblemStatus.DRAFT, false, 7L, CREATED, draft,
                deleted);
    }

    private static ArtifactCatalog emptyCatalog() {
        return new ArtifactCatalog(new ArtifactManifestRepository() {
            @Override
            public boolean liveSlotExists(long draftId, ArtifactKind kind, int caseOrder) {
                return false;
            }

            @Override
            public boolean liveGroupExists(long draftId, ArtifactKind kind, int caseOrder) {
                return false;
            }

            @Override
            public int countLiveGroups(long draftId) {
                return 0;
            }

            @Override
            public long insert(ProblemArtifact artifact) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ArtifactManifest> findLiveByProblemId(long problemId) {
                return List.of();
            }
        });
    }

    private static final class RecordingAudit implements ProblemAudit {
        private final List<ProblemAuditEntry> entries = new ArrayList<>();

        @Override
        public void record(ProblemAuditEntry entry) {
            entries.add(entry);
        }
    }

    private static final class FakeRepository implements ProblemDraftRepository {
        private ProblemDetailRecord detail = detail(false);
        private ProblemDraftRepository.LockedDraft locked;
        private DraftContent insertedContent;
        private int nextProblemNo = 1000;
        private long problemId = 11L;
        private long draftId = 22L;
        private boolean updateResult = true;
        private boolean deleteResult = true;
        private boolean advanceResult = true;
        private int updateCalls;
        private int deleteCalls;
        private List<ProblemListItem> pageItems = List.of();
        private long total;
        private String pageTitle;
        private Difficulty pageDifficulty;
        private boolean pageIncludeDeleted;
        private long pageOffset;
        private int pageLimit;
        private long advanceDraftId;
        private long advanceExpected;

        @Override
        public int allocateProblemNo() {
            return nextProblemNo++;
        }

        @Override
        public long insertProblem(int problemNo, long createdById) {
            return problemId;
        }

        @Override
        public long insertDraft(long problemId, DraftContent content, long createdById) {
            this.insertedContent = content;
            return draftId;
        }

        @Override
        public void attachDraft(long problemId, long draftId) {
            // no-op
        }

        @Override
        public java.util.Optional<ProblemDetailRecord> findDetail(long problemId) {
            return java.util.Optional.of(detail);
        }

        @Override
        public java.util.Optional<ProblemDetailRecord> lockDetail(long problemId) {
            return java.util.Optional.of(detail);
        }

        @Override
        public boolean updateDraft(long problemId, long draftId, long expectedRevision,
                                   DraftContent content, long updatedById) {
            updateCalls++;
            if (!updateResult) {
                return false;
            }
            ProblemDraft previous = detail.draft();
            ProblemDraft revised = new ProblemDraft(previous.id(), previous.problemId(),
                    previous.revision() + 1, content, previous.createdById(), updatedById,
                    previous.createdAt(), UPDATED, previous.deleted());
            detail = new ProblemDetailRecord(detail.problemId(), detail.problemNo(), detail.status(),
                    detail.everPublished(), detail.createdById(), detail.createdAt(), revised,
                    detail.deleted());
            return true;
        }

        @Override
        public boolean advanceRevision(long draftId, long expectedRevision) {
            advanceDraftId = draftId;
            advanceExpected = expectedRevision;
            return advanceResult;
        }

        @Override
        public boolean markDeleted(long problemId, long draftId, long updatedById) {
            deleteCalls++;
            if (!deleteResult) {
                return false;
            }
            ProblemDraft previous = detail.draft();
            ProblemDraft deletedDraft = new ProblemDraft(previous.id(), previous.problemId(),
                    previous.revision(), previous.content(), previous.createdById(), updatedById,
                    previous.createdAt(), UPDATED, true);
            detail = new ProblemDetailRecord(detail.problemId(), detail.problemNo(),
                    ProblemStatus.DELETED, detail.everPublished(), detail.createdById(),
                    detail.createdAt(), deletedDraft, true);
            return true;
        }

        @Override
        public List<ProblemListItem> findPage(String escapedTitle, Difficulty difficulty,
                                              boolean includeDeleted, long offset, int limit) {
            pageTitle = escapedTitle;
            pageDifficulty = difficulty;
            pageIncludeDeleted = includeDeleted;
            pageOffset = offset;
            pageLimit = limit;
            return pageItems;
        }

        @Override
        public long count(String escapedTitle, Difficulty difficulty, boolean includeDeleted) {
            return total;
        }

        @Override
        public ProblemDraftRepository.LockedDraft lockLiveDraft(long problemId) {
            return locked;
        }
    }
}
