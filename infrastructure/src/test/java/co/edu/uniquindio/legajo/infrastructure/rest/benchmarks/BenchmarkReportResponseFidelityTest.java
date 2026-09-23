package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.infrastructure.benchmarks.CsvBenchmarkReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §6.6 {@code GET /benchmarks} (1.3.10), R3-as-is-fidelity-unproved: proves that a value
 * written to the versioned CSV export reaches the {@code GET /api/v1/benchmarks} JSON body
 * unchanged, all the way through {@link CsvBenchmarkReportRepository#load()} and {@link
 * BenchmarkReportResponse#from(BenchmarkReport)} — the two hops between the CSV file and the
 * wire — for every field the port's own Javadoc says is served "as-is, without recalculating
 * anything": {@code score}, {@code error}, {@code unit}, {@code size}, {@code empiricalSlope},
 * {@code points}, and {@code theoreticalExponent}. Deliberately distinctive numbers (not the
 * real reference-run CSVs, which could coincidentally match a hand-picked expectation) so a
 * mapping bug that swaps or drops a field cannot go unnoticed.
 */
class BenchmarkReportResponseFidelityTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final List<String> HARNESS_HEADER = List.of(
            "# harness.cpuModel = fidelity-test-cpu",
            "# harness.logicalCores = 4",
            "# harness.totalRamBytes = 8000000000",
            "# harness.jdk = fidelity-test-jdk",
            "# harness.os = fidelity-test-os",
            "# harness.utcDate = 2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void everyCsvValueReachesTheJsonResponseUnchanged() throws IOException {
        // Distinctive size/score/error/unit, deliberately not round numbers.
        String resultRow = "co.example.Bench.compare,levenshtein,length,777.0,123.456789,0.000042,ns/op";
        // Distinctive points/empiricalSlope/theoreticalExponent.
        String slopeRow = "levenshtein,9,1.234567,3.000000";

        Path results = writeCsv("jmh-results.csv", HARNESS_HEADER,
                "benchmark,family,parameter,size,score,error,unit", List.of(resultRow));
        Path slopes = writeCsv("slopes.csv", List.of(),
                "family,points,empiricalSlope,theoreticalExponent", List.of(slopeRow));

        BenchmarkReport report = new CsvBenchmarkReportRepository(results, slopes).load();
        BenchmarkReportResponse response = BenchmarkReportResponse.from(report);
        JsonNode json = JSON.valueToTree(response);

        JsonNode resultNode = json.get("results").get(0);
        assertThat(resultNode.get("benchmark").asString()).isEqualTo("co.example.Bench.compare");
        assertThat(resultNode.get("family").asString()).isEqualTo("levenshtein");
        assertThat(resultNode.get("parameter").asString()).isEqualTo("length");
        assertThat(resultNode.get("size").asDouble()).isEqualTo(777.0);
        assertThat(resultNode.get("score").asDouble()).isEqualTo(123.456789);
        assertThat(resultNode.get("error").asDouble()).isEqualTo(0.000042);
        assertThat(resultNode.get("unit").asString()).isEqualTo("ns/op");

        JsonNode slopeNode = json.get("slopes").get(0);
        assertThat(slopeNode.get("family").asString()).isEqualTo("levenshtein");
        assertThat(slopeNode.get("points").asInt()).isEqualTo(9);
        assertThat(slopeNode.get("empiricalSlope").asDouble()).isEqualTo(1.234567);
        assertThat(slopeNode.get("theoreticalExponent").asDouble()).isEqualTo(3.000000);

        JsonNode harnessNode = json.get("harness");
        assertThat(harnessNode.get("cpuModel").asString()).isEqualTo("fidelity-test-cpu");
        assertThat(harnessNode.get("logicalCores").asInt()).isEqualTo(4);
        assertThat(harnessNode.get("totalRamBytes").asLong()).isEqualTo(8_000_000_000L);
        assertThat(harnessNode.get("jdk").asString()).isEqualTo("fidelity-test-jdk");
        assertThat(harnessNode.get("os").asString()).isEqualTo("fidelity-test-os");
        assertThat(harnessNode.get("measuredAt").asString()).isEqualTo("2026-01-01T00:00:00Z");
    }

    private Path writeCsv(String fileName, List<String> headerLines, String columnHeader, List<String> rows) {
        try {
            Path path = tempDir.resolve(fileName);
            var lines = new java.util.ArrayList<>(headerLines);
            lines.add(columnHeader);
            lines.addAll(rows);
            Files.write(path, lines, StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
