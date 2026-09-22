package co.edu.uniquindio.legajo.benchmarks.export;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads one JMH {@code -rf JSON} results file (the {@code :benchmarks:jmh} task's
 * {@code build/results/jmh/jmh-results.json}, configured in {@code build.gradle.kts}) into
 * {@link JmhResultRecord}s.
 *
 * <p>Configures {@link JsonReadFeature#ALLOW_NON_NUMERIC_NUMBERS}: JMH itself writes a
 * single-fork {@code scoreError} as the bare (unquoted, non-standard) JSON token
 * {@code NaN}, which strict JSON parsing rejects.
 */
public final class JmhJsonResultsReader {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .build();

    private JmhJsonResultsReader() {
    }

    /** Reads and parses every entry of {@code resultsJson} (a JMH {@code -rf JSON} array). */
    public static List<JmhResultRecord> read(Path resultsJson) {
        try (InputStream in = Files.newInputStream(resultsJson)) {
            JmhRawEntry[] entries = MAPPER.readValue(in, JmhRawEntry[].class);
            return List.of(entries).stream().map(JmhRawEntry::toResultRecord).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read JMH results from " + resultsJson, e);
        }
    }
}
