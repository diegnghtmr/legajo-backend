package co.edu.uniquindio.legajo.benchmarks.input;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves {@code data/corpus.json} regardless of which directory the current process was
 * launched from (the backend root, {@code benchmarks/}, or anywhere else under the backend
 * tree) rather than relying on a specific Gradle task's working directory being configured
 * to match another module's convention. The real-corpus SLO benchmarks (NFR-QA-01,
 * NFR-QA-02) and their setup all resolve the corpus through this one class.
 */
public final class CorpusPaths {

    private static final String RELATIVE_PATH = "data/corpus.json";
    private static final String OVERRIDE_PROPERTY = "legajo.benchmarks.corpusPath";

    private CorpusPaths() {
    }

    /**
     * Returns the resolved path to {@code data/corpus.json}: the system property
     * {@code -Dlegajo.benchmarks.corpusPath=<path>} when set, otherwise the first
     * {@code data/corpus.json} found while walking up from the current working directory.
     */
    public static Path resolveCorpusJson() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }

        Path cwd = Path.of("").toAbsolutePath();
        for (Path candidateDirectory = cwd; candidateDirectory != null;
                candidateDirectory = candidateDirectory.getParent()) {
            Path candidate = candidateDirectory.resolve(RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not find " + RELATIVE_PATH + " above " + cwd
                        + "; pass -D" + OVERRIDE_PROPERTY + "=<path> to override");
    }
}
