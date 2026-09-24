package co.edu.uniquindio.legajo.benchmarks.export;

import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * Reads and parses every entry of {@code resultsJson} (a JMH {@code -rf JSON} array).
     * Malformed or truncated JSON fails with a message naming {@code resultsJson}: Jackson 3
     * raises its parsing failures as the unchecked {@link JacksonException}, which by itself
     * carries no reference to the file this reader opened it from.
     */
    public static List<JmhResultRecord> read(Path resultsJson) {
        return Arrays.stream(parse(resultsJson)).map(JmhRawEntry::toResultRecord).toList();
    }

    /**
     * The {@code jdkVersion} JMH itself recorded for this run, if any entry reports one (the
     * first non-null value found; every entry of one run shares the same JDK). Used by
     * {@link JmhExportCli} for a cheap cross-check against the harness sidecar captured at
     * {@code :benchmarks:jmh} run time, since the harness must describe the machine that
     * actually produced the measurements, never a machine or session {@code jmhExport}
     * happens to run on later; empty when no entry declares it.
     */
    public static Optional<String> readReportedJdkVersion(Path resultsJson) {
        return Arrays.stream(parse(resultsJson)).map(JmhRawEntry::jdkVersion).filter(Objects::nonNull).findFirst();
    }

    private static JmhRawEntry[] parse(Path resultsJson) {
        try (InputStream in = Files.newInputStream(resultsJson)) {
            return MAPPER.readValue(in, JmhRawEntry[].class);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read JMH results from " + resultsJson, e);
        } catch (JacksonException e) {
            throw new IllegalStateException("failed to parse JMH results JSON from " + resultsJson, e);
        }
    }
}
