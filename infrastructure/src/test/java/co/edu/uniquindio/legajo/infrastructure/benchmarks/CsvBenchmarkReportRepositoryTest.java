package co.edu.uniquindio.legajo.infrastructure.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRD §6.6 {@code GET /benchmarks} (1.3.10): {@link CsvBenchmarkReportRepository} reads the
 * versioned {@code jmh-results.csv}/{@code slopes.csv} exports as-is, never recomputing
 * anything, and fails closed — naming the offending file and the export command,
 * {@code ./gradlew :benchmarks:jmh :benchmarks:jmhExport} — on every malformed shape the
 * feature task anticipates: a missing file, a missing harness header key, a data row with
 * the wrong column count, and a non-numeric value where a number is expected.
 */
class CsvBenchmarkReportRepositoryTest {

    private static final String EXPORT_COMMAND = "./gradlew :benchmarks:jmh :benchmarks:jmhExport";

    private static final List<String> VALID_HEADER = List.of(
            "# harness.cpuModel = 12th Gen Intel(R) Core(TM) i9-12900H",
            "# harness.logicalCores = 20",
            "# harness.totalRamBytes = 33363460096",
            "# harness.jdk = Eclipse Adoptium 25.0.4",
            "# harness.os = Linux 7.2.5-3-omarchy (amd64)",
            "# harness.utcDate = 2026-09-23T00:43:04.800549029Z");

    private static final String RESULTS_COLUMN_HEADER = "benchmark,family,parameter,size,score,error,unit";
    private static final String RESULTS_ROW =
            "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.compare,levenshtein,length,50.0,123.45,6.78,ns/op";
    private static final String SLOPES_COLUMN_HEADER = "family,points,empiricalSlope,theoreticalExponent";
    private static final String SLOPES_ROW = "levenshtein,5,2.039288,2.000000";

    @TempDir
    Path tempDir;

    @Test
    void loadsTheHarnessResultsAndSlopesFromWellFormedCsvs() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        BenchmarkReport report = new CsvBenchmarkReportRepository(results, slopes).load();

        assertThat(report.harness().cpuModel()).isEqualTo("12th Gen Intel(R) Core(TM) i9-12900H");
        assertThat(report.harness().logicalCores()).isEqualTo(20);
        assertThat(report.harness().totalRamBytes()).isEqualTo(33363460096L);
        assertThat(report.harness().jdk()).isEqualTo("Eclipse Adoptium 25.0.4");
        assertThat(report.harness().os()).isEqualTo("Linux 7.2.5-3-omarchy (amd64)");
        assertThat(report.harness().measuredAt()).isEqualTo("2026-09-23T00:43:04.800549029Z");

        assertThat(report.results()).hasSize(1);
        assertThat(report.results().getFirst().benchmark())
                .isEqualTo("co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.compare");
        assertThat(report.results().getFirst().family()).isEqualTo("levenshtein");
        assertThat(report.results().getFirst().parameter()).isEqualTo("length");
        assertThat(report.results().getFirst().size()).isEqualTo(50.0);
        assertThat(report.results().getFirst().score()).isEqualTo(123.45);
        assertThat(report.results().getFirst().error()).isEqualTo(6.78);
        assertThat(report.results().getFirst().unit()).isEqualTo("ns/op");

        assertThat(report.slopes()).hasSize(1);
        assertThat(report.slopes().getFirst().family()).isEqualTo("levenshtein");
        assertThat(report.slopes().getFirst().points()).isEqualTo(5);
        assertThat(report.slopes().getFirst().empiricalSlope()).isEqualTo(2.039288);
        assertThat(report.slopes().getFirst().theoreticalExponent()).isEqualTo(2.0);
    }

    @Test
    void missingResultsFileFailsNamingTheFileAndTheExportCommand() {
        Path results = tempDir.resolve("does-not-exist-results.csv");
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(results.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void missingSlopesFileFailsNamingTheFileAndTheExportCommand() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = tempDir.resolve("does-not-exist-slopes.csv");

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(slopes.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void missingHarnessHeaderKeyFailsNamingTheKeyAndTheExportCommand() {
        List<String> headerMissingOs = VALID_HEADER.stream().filter(line -> !line.contains("harness.os")).toList();
        Path results = writeResults(headerMissingOs, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("harness.os")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void wrongColumnCountInAResultsRowFails() {
        Path results = writeResults(VALID_HEADER, List.of("levenshtein,length,50.0"));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 7")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void wrongColumnCountInASlopesRowFails() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of("levenshtein,5,2.039288"));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected 4")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void nonNumericScoreInAResultsRowFails() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,not-a-number,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score")
                .hasMessageContaining("not-a-number")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void nonNumericEmpiricalSlopeInASlopesRowFails() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of("levenshtein,5,not-a-number,2.000000"));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empiricalSlope")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void nonNumericLogicalCoresInTheHarnessHeaderFails() {
        List<String> badHeader = VALID_HEADER.stream()
                .map(line -> line.startsWith("# harness.logicalCores") ? "# harness.logicalCores = not-a-number" : line)
                .toList();
        Path results = writeResults(badHeader, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logicalCores")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    private Path writeResults(List<String> headerLines, List<String> rows) {
        Path path = tempDir.resolve("jmh-results-" + UUID.randomUUID() + ".csv");
        return writeCsv(path, headerLines, RESULTS_COLUMN_HEADER, rows);
    }

    private Path writeSlopes(List<String> rows) {
        Path path = tempDir.resolve("slopes-" + UUID.randomUUID() + ".csv");
        return writeCsv(path, List.of(), SLOPES_COLUMN_HEADER, rows);
    }

    private Path writeCsv(Path path, List<String> headerLines, String columnHeader, List<String> rows) {
        try {
            List<String> lines = new ArrayList<>(headerLines);
            lines.add(columnHeader);
            lines.addAll(rows);
            Files.write(path, lines, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
