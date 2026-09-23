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

    /**
     * With two missing keys, the failure must list both, in the registry's fixed order
     * (cpuModel, logicalCores, totalRamBytes, jdk, os, utcDate), never just the first one it
     * happens to notice missing — otherwise fixing one key at a time would hide the other
     * failure behind a new error on every re-run.
     */
    @Test
    void missingTwoHarnessHeaderKeysListsBothInTheFixedOrder() {
        List<String> headerMissingJdkAndCpuModel = VALID_HEADER.stream()
                .filter(line -> !line.contains("harness.jdk") && !line.contains("harness.cpuModel"))
                .toList();
        Path results = writeResults(headerMissingJdkAndCpuModel, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(results + " is missing header keys 'harness.cpuModel', 'harness.jdk'; re-run "
                        + EXPORT_COMMAND);
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

    @Test
    void rejectsNaNAsAScore() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,NaN,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score")
                .hasMessageContaining("NaN")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsPositiveInfinityAsAnError() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,123.45,Infinity,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("error")
                .hasMessageContaining("Infinity")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsNegativeInfinityAsASize() {
        String badRow = "co.example.Bench.compare,levenshtein,length,-Infinity,123.45,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size")
                .hasMessageContaining("-Infinity")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsAnOverflowingDecimalThatParsesToInfinityAsAnEmpiricalSlope() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of("levenshtein,5,1e400,2.000000"));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empiricalSlope")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsAHexFloatLiteralAsAScore() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,0x1.8p3,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score")
                .hasMessageContaining("0x1.8p3")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsADoubleSuffixOnAScore() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,123.45d,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score")
                .hasMessageContaining("123.45d")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsAFloatSuffixOnAnError() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,123.45,6.78f,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("error")
                .hasMessageContaining("6.78f")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void rejectsSurroundingWhitespaceInAScore() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0, 123.45 ,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("score")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void invalidUtf8BytesInTheResultsFileFailAsAnIllegalStateException() throws IOException {
        Path results = tempDir.resolve("jmh-results-" + UUID.randomUUID() + ".csv");
        List<String> validLines = new ArrayList<>(VALID_HEADER);
        validLines.add(RESULTS_COLUMN_HEADER);
        validLines.add(RESULTS_ROW);
        try (var out = Files.newOutputStream(results)) {
            out.write(String.join("\n", validLines).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            // A lone continuation byte is not valid UTF-8 on its own.
            out.write(new byte[] {'b', 'a', 'd', (byte) 0x80, 'r', 'o', 'w', '\n'});
        }
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(results.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void aDirectoryInPlaceOfTheResultsFileFailsAsAnIllegalStateExceptionNotAnUncheckedIOException()
            throws IOException {
        Path results = Files.createDirectory(tempDir.resolve("results-is-a-dir-" + UUID.randomUUID()));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(results.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void resultsFileWithAHeaderButZeroDataRowsFailsClosed() {
        Path results = writeResults(VALID_HEADER, List.of());
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(results.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void slopesFileWithAHeaderButZeroDataRowsFailsClosed() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of());

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(slopes.toString())
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankCpuModelInTheHarnessHeaderFailsClosed() {
        List<String> headerBlankCpu = VALID_HEADER.stream()
                .map(line -> line.startsWith("# harness.cpuModel") ? "# harness.cpuModel =   " : line)
                .toList();
        Path results = writeResults(headerBlankCpu, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cpuModel")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankBenchmarkNameInAResultsRowFailsClosed() {
        String badRow = ",levenshtein,length,50.0,123.45,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("benchmark")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankFamilyInAResultsRowFailsClosed() {
        String badRow = "co.example.Bench.compare,,length,50.0,123.45,6.78,ns/op";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("family")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankUnitInAResultsRowFailsClosed() {
        String badRow = "co.example.Bench.compare,levenshtein,length,50.0,123.45,6.78,";
        Path results = writeResults(VALID_HEADER, List.of(badRow));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unit")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankFamilyInASlopesRowFailsClosed() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(",5,2.039288,2.000000"));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("family")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void duplicateHarnessHeaderKeyFailsClosed() {
        List<String> headerWithDuplicate = new ArrayList<>(VALID_HEADER);
        headerWithDuplicate.add("# harness.cpuModel = a different cpu");
        Path results = writeResults(headerWithDuplicate, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate")
                .hasMessageContaining("cpuModel")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void unknownHarnessHeaderKeyFailsClosed() {
        List<String> headerWithUnknown = new ArrayList<>(VALID_HEADER);
        headerWithUnknown.add("# harness.gpuModel = RTX 4090");
        Path results = writeResults(headerWithUnknown, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gpuModel")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void unrecognizedHeaderLineShapeFailsClosed() {
        List<String> headerWithComment = new ArrayList<>(VALID_HEADER);
        headerWithComment.add(0, "# just a plain comment, not a harness key");
        Path results = writeResults(headerWithComment, List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unrecognized")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void missingResultsColumnHeaderFailsClosed() {
        Path results = writeCsv(
                tempDir.resolve("jmh-results-" + UUID.randomUUID() + ".csv"),
                VALID_HEADER, "wrong,column,header", List.of(RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column header")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void missingSlopesColumnHeaderFailsClosed() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW));
        Path slopes = writeCsv(
                tempDir.resolve("slopes-" + UUID.randomUUID() + ".csv"),
                List.of(), "wrong,column,header", List.of(SLOPES_ROW));

        assertThatThrownBy(() -> new CsvBenchmarkReportRepository(results, slopes).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column header")
                .hasMessageContaining(EXPORT_COMMAND);
    }

    @Test
    void blankLinesInResultsAreSkippedAndDoNotCountAsDataRows() {
        Path results = writeResults(VALID_HEADER, List.of(RESULTS_ROW, "", RESULTS_ROW));
        Path slopes = writeSlopes(List.of(SLOPES_ROW));

        BenchmarkReport report = new CsvBenchmarkReportRepository(results, slopes).load();

        assertThat(report.results()).hasSize(2);
    }

    /**
     * Re-export drift guard: if a future {@code jmhExport} run changes the CSV shape (column
     * order, harness keys, quoting) without updating this parser, this test fails against the
     * real, versioned reference-harness files instead of only a hand-written fixture.
     */
    @Test
    void loadsTheCommittedReferenceRunCsvFilesWithoutError() {
        Path results = committedFile("benchmarks/results/jmh-results.csv");
        Path slopes = committedFile("benchmarks/results/slopes.csv");

        BenchmarkReport report = new CsvBenchmarkReportRepository(results, slopes).load();

        assertThat(report.harness().cpuModel()).isNotBlank();
        assertThat(report.results()).isNotEmpty();
        assertThat(report.slopes()).isNotEmpty();
    }

    /** Resolves a path repository-relative regardless of whether the test JVM's working
     * directory is the backend root or this module's own directory. */
    private static Path committedFile(String repositoryRelativePath) {
        Path fromBackendRoot = Path.of(repositoryRelativePath);
        if (Files.exists(fromBackendRoot)) {
            return fromBackendRoot;
        }
        Path fromModuleDirectory = Path.of("..").resolve(repositoryRelativePath);
        if (Files.exists(fromModuleDirectory)) {
            return fromModuleDirectory;
        }
        throw new AssertionError("could not locate " + repositoryRelativePath
                + " from either the backend root or a module directory");
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
