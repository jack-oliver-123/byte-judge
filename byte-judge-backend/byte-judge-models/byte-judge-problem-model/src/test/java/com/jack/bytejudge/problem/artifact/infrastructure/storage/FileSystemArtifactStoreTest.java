package com.jack.bytejudge.problem.artifact.infrastructure.storage;

import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.domain.ArtifactRules;
import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemArtifactStoreTest {

    private static final byte[] CONTENT = "case one\r\n1 2 3\r\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    @Test
    void storesContentUnderItsDigestAndNeverWritesTheClientName() throws IOException {
        ArtifactStore.StoredArtifact stored = store().store(new ByteArrayInputStream(CONTENT));

        assertThat(stored.contentDigest()).isEqualTo(sha256Hex(CONTENT));
        assertThat(stored.byteSize()).isEqualTo(CONTENT.length);
        Path object = onlyObject();
        assertThat(object.getFileName().toString()).isEqualTo(stored.contentDigest());
        assertThat(object.toString()).contains("objects");
        assertThat(Files.readAllBytes(object)).isEqualTo(CONTENT);
        assertThat(listFiles(root.resolve("tmp"))).as("temporary files are removed").isEmpty();
    }

    @Test
    void acceptsExactlyOneMebibyte() throws IOException {
        byte[] atBound = new byte[(int) ArtifactRules.MAX_BYTES];
        ArtifactStore.StoredArtifact stored = store().store(new ByteArrayInputStream(atBound));

        assertThat(stored.byteSize()).isEqualTo(ArtifactRules.MAX_BYTES);
        assertThat(Files.size(onlyObject())).isEqualTo(ArtifactRules.MAX_BYTES);
    }

    @Test
    void rejectsOneByteOverTheBoundWithoutPublishingAnything() throws IOException {
        byte[] tooLarge = new byte[(int) ArtifactRules.MAX_BYTES + 1];

        assertThatThrownBy(() -> store().store(new ByteArrayInputStream(tooLarge)))
                .isInstanceOf(ArtifactTooLargeException.class);

        assertThat(listFiles(root.resolve("objects"))).isEmpty();
        assertThat(listFiles(root.resolve("tmp"))).isEmpty();
    }

    @Test
    void storesEmptyContentUnderTheEmptyDigest() throws IOException {
        ArtifactStore.StoredArtifact stored = store().store(new ByteArrayInputStream(new byte[0]));

        assertThat(stored.byteSize()).isZero();
        assertThat(stored.contentDigest()).isEqualTo(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(Files.size(onlyObject())).isZero();
    }

    @Test
    void emptyContentIsDeduplicatedLikeAnyOtherObject() throws IOException {
        ArtifactStore store = store();

        ArtifactStore.StoredArtifact first = store.store(new ByteArrayInputStream(new byte[0]));
        ArtifactStore.StoredArtifact second = store.store(new ByteArrayInputStream(new byte[0]));

        assertThat(second).isEqualTo(first);
        assertThat(listFiles(root.resolve("objects"))).hasSize(1);
    }

    @Test
    void identicalContentIsDeduplicatedToASingleVerifiedObject() throws IOException {
        ArtifactStore store = store();

        ArtifactStore.StoredArtifact first = store.store(new ByteArrayInputStream(CONTENT));
        ArtifactStore.StoredArtifact second = store.store(
                new ByteArrayInputStream(CONTENT.clone()));

        assertThat(second).isEqualTo(first);
        assertThat(listFiles(root.resolve("objects"))).hasSize(1);
        assertThat(Files.readAllBytes(onlyObject())).isEqualTo(CONTENT);
    }

    @Test
    void aCorruptedPreexistingObjectFailsClosedInsteadOfBeingReused() throws IOException {
        ArtifactStore store = store();
        ArtifactStore.StoredArtifact stored = store.store(new ByteArrayInputStream(CONTENT));
        Path object = onlyObject();

        byte[] tampered = CONTENT.clone();
        tampered[0] ^= 0x01;
        Files.write(object, tampered);

        assertThatThrownBy(() -> store.store(new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(ArtifactStorageException.class);
        assertThat(Files.readAllBytes(object))
                .as("the adapter must not silently repair or replace the object")
                .isEqualTo(tampered);
    }

    @Test
    void concurrentUploadsOfIdenticalContentConvergeOnOneObject() throws Exception {
        ArtifactStore store = store();
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ArtifactStore.StoredArtifact>> work = new ArrayList<>();
        for (int index = 0; index < writers; index++) {
            work.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                return store.store(new ByteArrayInputStream(CONTENT.clone()));
            });
        }

        List<Future<ArtifactStore.StoredArtifact>> results = new ArrayList<>();
        for (Callable<ArtifactStore.StoredArtifact> task : work) {
            results.add(pool.submit(task));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        String expected = sha256Hex(CONTENT);
        for (Future<ArtifactStore.StoredArtifact> result : results) {
            assertThat(result.get().contentDigest()).isEqualTo(expected);
        }
        assertThat(listFiles(root.resolve("objects"))).hasSize(1);
        assertThat(Files.readAllBytes(onlyObject())).isEqualTo(CONTENT);
        assertThat(listFiles(root.resolve("tmp"))).isEmpty();
    }

    @Test
    void concurrentUploadsOfDifferentContentRemainIndependent() throws Exception {
        ArtifactStore store = store();
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ArtifactStore.StoredArtifact>> results = new ArrayList<>();
        for (int index = 0; index < writers; index++) {
            byte[] content = ("content-" + index).getBytes(StandardCharsets.UTF_8);
            results.add(pool.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return store.store(new ByteArrayInputStream(content));
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<String> digests = new ArrayList<>();
        for (Future<ArtifactStore.StoredArtifact> result : results) {
            digests.add(result.get().contentDigest());
        }
        assertThat(digests).doesNotHaveDuplicates();
        assertThat(listFiles(root.resolve("objects"))).hasSize(writers);
    }

    private FileSystemArtifactStore store() {
        return new FileSystemArtifactStore(root, ArtifactRules.MAX_BYTES);
    }

    private Path onlyObject() throws IOException {
        List<Path> objects = listFiles(root.resolve("objects"));
        assertThat(objects).hasSize(1);
        return objects.get(0);
    }

    private static List<Path> listFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
