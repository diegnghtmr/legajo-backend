package co.edu.uniquindio.legajo.benchmarks.input;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves {@code data/corpus.json} regardless of which directory the current process was
 * launched from (the backend root, {@code benchmarks/}, or anywhere else under the backend
 * tree) rather than relying on a specific Gradle task's working directory being configured
 * to match another module's convention. The real-corpus SLO benchmarks (classic-pairwise and
 * clustering) and their setup all resolve the corpus through this one class.
 */
public final class CorpusPaths {

    private static final String RELATIVE_PATH = "data/corpus.json";

    /** Package-private so {@code CorpusPathsTest} references this exact name instead of a duplicated string literal. */
    static final String OVERRIDE_PROPERTY = "legajo.benchmarks.corpusPath";

    private CorpusPaths() {
    }

    /**
     * Returns the resolved path to {@code data/corpus.json}: the system property
     * {@code -Dlegajo.benchmarks.corpusPath=<path>} when set, otherwise the first
     * {@code data/corpus.json} found while walking up from the current working directory.
     */
    public static Path resolveCorpusJson() {
        return resolveCorpusJson(Path.of("").toAbsolutePath());
    }

    /**
     * Same resolution {@link #resolveCorpusJson()} performs, but against an explicit starting
     * directory instead of the real process working directory. Package-private so a test can
     * exercise the "nothing found while walking up" failure from an isolated directory, without
     * depending on (or having to fake) the actual working directory the test JVM started in.
     */
    static Path resolveCorpusJson(Path cwd) {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            Path overridePath = Path.of(override);
            if (!Files.isRegularFile(overridePath)) {
                throw new IllegalStateException(
                        "-D" + OVERRIDE_PROPERTY + "=" + override + " does not point to a regular file: "
                                + overridePath);
            }
            return overridePath;
        }

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
