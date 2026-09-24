package co.edu.uniquindio.legajo.infrastructure.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Shared write-then-move helper for {@code corpus.json} and {@code embeddings-*.json},
 * fixing a robustness gap: writing the full new content directly to the target
 * path with {@code TRUNCATE_EXISTING} (the previous behavior of both {@code
 * JsonCorpusRepository} and {@code JsonEmbeddingRepository}) truncates the file before
 * the new bytes are fully on disk, so a crash mid-write — a killed process, a disk-full
 * condition, a power loss — leaves a corrupt, incomplete file and the previous,
 * possibly still-valid content is already gone.
 *
 * <p>Instead, this writes the complete content to a temporary file created in the
 * <em>same directory</em> as the target (required for {@link StandardCopyOption#ATOMIC_MOVE}
 * to be atomic — it only guarantees atomicity within one file store) and then moves it
 * into place. Every failure mode either leaves the temporary file as the only casualty
 * (the target is untouched) or completes as a single atomic rename that can never be
 * observed half-done. When the platform or file store does not support an atomic move
 * (e.g. some network file systems), this falls back to a plain, non-atomic {@link
 * StandardCopyOption#REPLACE_EXISTING} move — still strictly safer than truncating the
 * target directly, since the new content is fully written to the temporary file first.
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {
    }

    public static void writeUtf8(Path targetPath, String content) {
        Objects.requireNonNull(targetPath, "targetPath");
        Objects.requireNonNull(content, "content");

        Path absoluteTarget = targetPath.toAbsolutePath();
        Path parentDirectory = absoluteTarget.getParent();
        Path temporaryFile;
        try {
            temporaryFile = Files.createTempFile(parentDirectory, tempFilePrefix(absoluteTarget), ".tmp");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to create a temporary file next to " + targetPath + " for an atomic write", e);
        }

        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile, targetPath);
        } catch (IOException e) {
            deleteQuietly(temporaryFile);
            throw new UncheckedIOException("Failed to write to " + targetPath, e);
        }
    }

    private static void moveIntoPlace(Path temporaryFile, Path targetPath) throws IOException {
        try {
            Files.move(temporaryFile, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporaryFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of a leftover temp file; the original IOException is what matters.
        }
    }

    private static String tempFilePrefix(Path absoluteTarget) {
        String fileName = absoluteTarget.getFileName() == null ? "atomic-write" : absoluteTarget.getFileName().toString();
        return fileName + "-";
    }
}
