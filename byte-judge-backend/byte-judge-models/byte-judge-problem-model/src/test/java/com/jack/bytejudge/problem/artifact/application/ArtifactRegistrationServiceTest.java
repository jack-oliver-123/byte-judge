package com.jack.bytejudge.problem.artifact.application;

import com.jack.bytejudge.problem.artifact.application.command.UploadArtifactCommand;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.application.port.ArtifactUnitOfWork;
import com.jack.bytejudge.problem.artifact.application.port.DraftArtifactSeam;
import com.jack.bytejudge.problem.artifact.application.result.ArtifactRegistration;
import com.jack.bytejudge.problem.artifact.domain.ArtifactRules;
import com.jack.bytejudge.problem.artifact.domain.ArtifactSlotOccupiedException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactTooLargeException;
import com.jack.bytejudge.problem.artifact.domain.InvalidArtifactException;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import com.jack.bytejudge.problem.artifact.domain.model.ArtifactManifest;
import com.jack.bytejudge.problem.artifact.domain.model.ProblemArtifact;
import com.jack.bytejudge.problem.artifact.domain.repository.ArtifactManifestRepository;
import com.jack.bytejudge.problem.audit.application.port.ProblemAudit;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEntry;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditEvent;
import com.jack.bytejudge.problem.audit.domain.model.ProblemAuditOutcome;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactRegistrationServiceTest {
    private static final byte[] CONTENT = "1 2\n".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = "a".repeat(64);

    private final List<String> events = new ArrayList<>();
    private final FakeUnitOfWork unitOfWork = new FakeUnitOfWork();
    private final FakeStore store = new FakeStore(unitOfWork, events);
    private final FakeManifests manifests = new FakeManifests(events);
    private final FakeSeam drafts = new FakeSeam(events);
    private final RecordingAudit audit = new RecordingAudit(events);
    private final ArtifactRegistrationService service = new ArtifactRegistrationService(store,
            manifests, drafts, audit, unitOfWork);

    @Test
    void storageRunsOutsideTheTransactionAndTheWriteOrderIsManifestRevisionAudit() {
        ArtifactRegistration registration = service.register(command(1, 10, "SAMPLE_INPUT",
                1, 0, ArtifactRules.MAX_BYTES, "cases/input.txt"));

        assertThat(store.seenInTransaction).isFalse();
        assertThat(events).containsExactly("store", "lock", "insert", "advance", "audit");
        assertThat(registration.artifactId()).isEqualTo(42L);
        assertThat(registration.contentDigest()).isEqualTo(DIGEST);
        assertThat(registration.fileName()).isEqualTo("input.txt");
        assertThat(manifests.inserted.draftId()).isEqualTo(77L);
        assertThat(manifests.inserted.problemId()).isEqualTo(1L);
        assertThat(audit.entries).singleElement().satisfies(entry -> {
            assertThat(entry.event()).isEqualTo(ProblemAuditEvent.ARTIFACT_UPLOAD);
            assertThat(entry.outcome()).isEqualTo(ProblemAuditOutcome.SUCCESS);
            assertThat(entry.reason()).isEqualTo("REGISTERED");
            assertThat(entry.problemId()).isEqualTo(1L);
            assertThat(entry.operatorId()).isEqualTo(9L);
            assertThat(entry.operatorAccount()).isEqualTo("admin");
            assertThat(entry.requestId()).isEqualTo("request-1");
        });
    }

    @Test
    void anOversizeDeclaredFileIsRejectedBeforeStorageAndAudited() {
        assertThatThrownBy(() -> service.register(command(1, 10, "SAMPLE_INPUT", 1, 0,
                ArtifactRules.MAX_BYTES + 1, "input.txt")))
                .isInstanceOf(ArtifactTooLargeException.class);

        assertThat(store.calls).isZero();
        assertThat(events).containsExactly("audit");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("SIZE"));
        assertThat(audit.entries.get(0).outcome()).isEqualTo(ProblemAuditOutcome.REJECTED);
    }

    @Test
    void anUnknownKindIsRejectedAndAudited() {
        assertThatThrownBy(() -> service.register(command(1, 10, "sample_input", 1, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(InvalidArtifactException.class);

        assertThat(store.calls).isZero();
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("INVALID"));
    }

    @Test
    void anEmptyFileIsAcceptedBecauseFR12AllowsAnEmptyInputOrOutput() {
        ArtifactRegistration registration = service.register(command(1, 10, "SAMPLE_INPUT",
                1, 0, 0, "empty.in"));

        assertThat(store.calls).isEqualTo(1);
        assertThat(registration.fileName()).isEqualTo("empty.in");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.outcome()).isEqualTo(ProblemAuditOutcome.SUCCESS));
    }

    @Test
    void aTwentyFirstDistinctGroupIsRejectedUnderTheDraftLock() {
        manifests.liveGroups = ArtifactRules.MAX_GROUPS;

        assertThatThrownBy(() -> service.register(command(1, 10, "HIDDEN_INPUT", 20, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(com.jack.bytejudge.problem.artifact.domain.ArtifactGroupLimitException.class);

        assertThat(manifests.inserted).isNull();
        assertThat(events).containsExactly("store", "lock", "audit");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("GROUP_LIMIT"));
    }

    @Test
    void anExistingGroupMayCompleteEvenAtTheGroupCap() {
        manifests.liveGroups = ArtifactRules.MAX_GROUPS;
        manifests.groupExists = true;

        ArtifactRegistration registration = service.register(command(1, 10, "HIDDEN_OUTPUT", 20, 0,
                CONTENT.length, "output.txt"));

        assertThat(registration.kind()).isEqualTo(ArtifactKind.HIDDEN_OUTPUT);
        assertThat(manifests.inserted).isNotNull();
    }

    @Test
    void anOccupiedSlotIsAnExplicitRejectionNotAReplacement() {
        manifests.slotExists = true;

        assertThatThrownBy(() -> service.register(command(1, 10, "SAMPLE_INPUT", 1, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(ArtifactSlotOccupiedException.class);

        assertThat(events).containsExactly("store", "lock", "audit");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("SLOT"));
        assertThat(manifests.inserted).isNull();
    }

    @Test
    void aStaleRevisionIsAuditedAsAConflictAndNothingIsWritten() {
        drafts.lockFailure = new DraftArtifactSeam.RevisionConflictException();

        assertThatThrownBy(() -> service.register(command(1, 10, "SAMPLE_INPUT", 1, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(DraftArtifactSeam.RevisionConflictException.class);

        assertThat(manifests.inserted).isNull();
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("REVISION"));
    }

    @Test
    void aMissingDraftIsAuditedAsNotFound() {
        drafts.lockFailure = new DraftArtifactSeam.DraftNotFoundException();

        assertThatThrownBy(() -> service.register(command(1, 10, "SAMPLE_INPUT", 1, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(DraftArtifactSeam.DraftNotFoundException.class);

        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void aLostRevisionRaceAfterTheInsertIsReportedAsAConflict() {
        drafts.advanceResult = false;

        assertThatThrownBy(() -> service.register(command(1, 10, "SAMPLE_INPUT", 1, 0,
                CONTENT.length, "input.txt")))
                .isInstanceOf(DraftArtifactSeam.RevisionConflictException.class);

        assertThat(events).containsExactly("store", "lock", "insert", "advance", "audit");
        assertThat(audit.entries).singleElement()
                .satisfies(entry -> assertThat(entry.reason()).isEqualTo("REVISION"));
    }

    private static UploadArtifactCommand command(long problemId, long expectedRevision, String kind,
                                                 int caseOrder, int displayOrder, long declaredSize,
                                                 String fileName) {
        InputStream content = new ByteArrayInputStream(CONTENT);
        return new UploadArtifactCommand(problemId, expectedRevision, kind, caseOrder, displayOrder,
                declaredSize, fileName, content, 9L, "admin", "request-1");
    }

    private static final class FakeUnitOfWork implements ArtifactUnitOfWork {
        private boolean inTransaction;

        @Override
        public <T> T execute(Supplier<T> work) {
            inTransaction = true;
            try {
                return work.get();
            } finally {
                inTransaction = false;
            }
        }
    }

    private static final class FakeStore implements ArtifactStore {
        private final FakeUnitOfWork unitOfWork;
        private final List<String> events;
        private boolean seenInTransaction;
        private int calls;

        private FakeStore(FakeUnitOfWork unitOfWork, List<String> events) {
            this.unitOfWork = unitOfWork;
            this.events = events;
        }

        @Override
        public StoredArtifact store(InputStream content) {
            calls++;
            events.add("store");
            seenInTransaction = unitOfWork.inTransaction;
            return new StoredArtifact(DIGEST, CONTENT.length);
        }
    }

    private static final class FakeManifests implements ArtifactManifestRepository {
        private final List<String> events;
        private boolean slotExists;
        private boolean groupExists;
        private int liveGroups;
        private ProblemArtifact inserted;

        private FakeManifests(List<String> events) {
            this.events = events;
        }

        @Override
        public boolean liveSlotExists(long draftId, ArtifactKind kind, int caseOrder) {
            return slotExists;
        }

        @Override
        public boolean liveGroupExists(long draftId, ArtifactKind kind, int caseOrder) {
            return groupExists;
        }

        @Override
        public int countLiveGroups(long draftId) {
            return liveGroups;
        }

        @Override
        public long insert(ProblemArtifact artifact) {
            events.add("insert");
            inserted = artifact;
            return 42L;
        }

        @Override
        public List<ArtifactManifest> findLiveByProblemId(long problemId) {
            return List.of();
        }
    }

    private static final class FakeSeam implements DraftArtifactSeam {
        private final List<String> events;
        private RuntimeException lockFailure;
        private boolean advanceResult = true;

        private FakeSeam(List<String> events) {
            this.events = events;
        }

        @Override
        public long lockForArtifact(long problemId, long expectedRevision) {
            events.add("lock");
            if (lockFailure != null) {
                throw lockFailure;
            }
            return 77L;
        }

        @Override
        public boolean advanceRevision(long draftId, long expectedRevision) {
            events.add("advance");
            return advanceResult;
        }
    }

    private static final class RecordingAudit implements ProblemAudit {
        private final List<String> events;
        private final List<ProblemAuditEntry> entries = new ArrayList<>();

        private RecordingAudit(List<String> events) {
            this.events = events;
        }

        @Override
        public void record(ProblemAuditEntry entry) {
            events.add("audit");
            entries.add(entry);
        }
    }
}
