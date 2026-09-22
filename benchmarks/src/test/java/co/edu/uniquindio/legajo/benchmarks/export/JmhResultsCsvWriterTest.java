package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@code jmh-results.csv} writer (TRD NFR-QA-10, TAC-18: harness header +
 * {@code benchmark,family,parameter,size,score,error,unit} columns). Written before
 * {@link JmhResultsCsvWriter} exists (odd/tasks/jmh-benchmarks.md, task J2: strict TDD).
 */
class JmhResultsCsvWriterTest {

    private static final HarnessInfo HARNESS =
            new HarnessInfo("Test CPU", 4, 8_000_000_000L, "Temurin 25", "Linux 6.0 (amd64)", "2026-09-22T00:00:00Z");

    @Test
    void writesTheHarnessHeaderLinesFirst(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<JmhResultRecord> records = List.of(oneRecord());

        JmhResultsCsvWriter.write(output, HARNESS, records);

        List<String> lines = Files.readAllLines(output);
        long headerLineCount = lines.stream().takeWhile(line -> line.startsWith("#")).count();
        assertThat(headerLineCount).isEqualTo(HARNESS.toHeaderLines().size());
        assertThat(lines.get(0)).isEqualTo(HARNESS.toHeaderLines().get(0));
    }

    @Test
    void writesTheColumnHeaderRightAfterTheHarnessLines(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<JmhResultRecord> records = List.of(oneRecord());

        JmhResultsCsvWriter.write(output, HARNESS, records);

        List<String> lines = Files.readAllLines(output);
        assertThat(lines.get(HARNESS.toHeaderLines().size()))
                .isEqualTo("benchmark,family,parameter,size,score,error,unit");
    }

    @Test
    void writesOneDataRowPerRecord(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<JmhResultRecord> records = List.of(oneRecord(), anotherRecord());

        JmhResultsCsvWriter.write(output, HARNESS, records);

        List<String> lines = Files.readAllLines(output);
        int firstDataLine = HARNESS.toHeaderLines().size() + 1;
        assertThat(lines).hasSize(firstDataLine + 2);
        assertThat(lines.get(firstDataLine)).isEqualTo(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute,"
                        + "levenshtein,length,50.0,10.5,NaN,us/op");
        assertThat(lines.get(firstDataLine + 1)).isEqualTo(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute,"
                        + "levenshtein,length,100.0,21.3,0.42,us/op");
    }

    @Test
    void skipsARecordWithANonNumericSizeAndStillExportsTheOthers(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        JmhResultRecord badRecord = new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                Map.of("length", "not-a-number"), 1.0, 0.0, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
        List<JmhResultRecord> records = List.of(badRecord, oneRecord());

        JmhResultsCsvWriter.write(output, HARNESS, records);

        List<String> lines = Files.readAllLines(output);
        int firstDataLine = HARNESS.toHeaderLines().size() + 1;
        assertThat(lines).hasSize(firstDataLine + 1);
        assertThat(lines.get(firstDataLine)).contains("levenshtein,length,50.0,10.5");
    }

    private static JmhResultRecord oneRecord() {
        return new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                Map.of("length", "50"), 10.5, Double.NaN, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
    }

    private static JmhResultRecord anotherRecord() {
        return new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                Map.of("length", "100"), 21.3, 0.42, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
    }
}
