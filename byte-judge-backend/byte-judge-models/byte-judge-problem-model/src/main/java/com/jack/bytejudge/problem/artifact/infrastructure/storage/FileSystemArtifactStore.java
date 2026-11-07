package com.jack.bytejudge.problem.artifact.infrastructure.storage;

import com.jack.bytejudge.problem.artifact.application.port.ArtifactStore;
import com.jack.bytejudge.problem.artifact.domain.ArtifactStorageException;
import com.jack.bytejudge.problem.artifact.domain.ArtifactTooLargeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Filesystem adapter for the protected artifact volume.
 *
 * <p>Objects are addressed only by their SHA-256 digest, under {@code <root>/objects/<ab>/<cd>/<digest>}.
 * The client-supplied file name never participates in path construction. Uploads are streamed with
 * a hard byte bound into {@code <root>/tmp} (the same filesystem), verified while being written,
 * then published with an atomic move. A pre-existing object is re-verified for both size and
 * digest before it is reused; a mismatch fails closed instead of publishing corrupt test data.
 *
 * <p>The adapter holds no mutable per-call state, so concurrent uploads can share one instance.
 */
public final class FileSystemArtifactStore implements ArtifactStore {
    private static final int BUFFER_SIZE = 8_192;
    private static final String OBJECTS = "objects";
    private static final String TEMP = "tmp";

    private final Path root;
    private final Path objects;
    private final Path temporary;
    private final long maxBytes;

    public FileSystemArtifactStore(Path root, long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("The artifact byte bound must be positive.");
        }
        if (root == null) {
            throw new IllegalArgumentException("The artifact root is required.");
        }
        this.root = root.toAbsolutePath().normalize();
        this.objects = this.root.resolve(OBJECTS);
        this.temporary = this.root.resolve(TEMP);
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(objects);
            Files.createDirectories(temporary);
            restrictDirectory(this.root);
            restrictDirectory(objects);
            restrictDirectory(temporary);
        } catch (IOException failure) {
            throw new ArtifactStorageException("The artifact volume is not usable.");
        }
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new ArtifactStorageException("The artifact volume is not writable.");
        }
    }

    @Override
    public StoredArtifact store(InputStream content) {
        Path temp = null;
        try {
            temp = Files.createTempFile(temporary, "upload-", ".part");
            Written written = writeAndDigest(content, temp);
            return publish(temp, written.digest(), written.size());
        } catch (IOException failure) {
            throw new ArtifactStorageException("The artifact volume rejected the upload.", failure);
        } finally {
            if (temp != null) {
                deleteQuietly(temp);
            }
        }
    }

    /**
     * Streams through a fixed-size buffer and rejects immediately when the total exceeds the
     * limit, without buffering the complete file or publishing an oversized object.
     */
    private Written writeAndDigest(InputStream content, Path temp) throws IOException {
        MessageDigest digest = sha256();
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new ArtifactTooLargeException(maxBytes);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return new Written(HexFormat.of().formatHex(digest.digest()), total);
    }

    private StoredArtifact publish(Path temp, String digest, long size) throws IOException {
        Path target = targetFor(digest);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(target, digest, size);
            return new StoredArtifact(digest, size);
        }
        Files.createDirectories(target.getParent());
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException raced) {
            // Another upload of identical bytes won the race. Its object is complete because it
            // was published by an atomic move, so verify it and reuse it.
            verifyExisting(target, digest, size);
            return new StoredArtifact(digest, size);
        } catch (IOException moveFailure) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyExisting(target, digest, size);
                return new StoredArtifact(digest, size);
            }
            throw new ArtifactStorageException("The artifact could not be published.", moveFailure);
        }
        restrictFile(target);
        return new StoredArtifact(digest, size);
    }

    /**
     * A pre-existing object is only reused when its name, size and content digest all agree.
     * Corruption is never repaired silently: publishing bytes that were not verified under their
     * own digest would let a later validation pass on tampered test data.
     */
    private void verifyExisting(Path target, String expectedDigest, long expectedSize) {
        try {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ArtifactStorageException("The existing artifact object is not a regular file.");
            }
            if (Files.size(target) != expectedSize) {
                throw new ArtifactStorageException("The existing artifact object has a different size.");
            }
            if (!digestOf(target).equals(expectedDigest)) {
                throw new ArtifactStorageException("The existing artifact object has a different digest.");
            }
        } catch (IOException failure) {
            throw new ArtifactStorageException("The existing artifact object cannot be verified.", failure);
        }
    }

    private String digestOf(Path file) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Path targetFor(String digest) {
        Path target = objects.resolve(digest.substring(0, 2))
                .resolve(digest.substring(2, 4))
                .resolve(digest)
                .normalize();
        if (!target.startsWith(root)) {
            throw new ArtifactStorageException("The artifact object escaped the protected volume.");
        }
        return target;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable in this runtime.");
        }
    }

    private static void restrictDirectory(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows and other non-POSIX filesystems rely on the deployment's ACLs.
        }
    }

    private static void restrictFile(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException notPosix) {
            // Windows and other non-POSIX filesystems rely on the deployment's ACLs.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover temporary file is unreferenced; cleanup is a later controlled operation.
        }
    }

    private record Written(String digest, long size) {
    }
}
