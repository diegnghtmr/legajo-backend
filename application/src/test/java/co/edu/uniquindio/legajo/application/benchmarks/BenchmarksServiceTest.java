package co.edu.uniquindio.legajo.application.benchmarks;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /benchmarks}: {@link BenchmarksService} is pure orchestration
 * — it never reads a file or recomputes anything itself, it only forwards to whichever
 * {@link BenchmarkReportRepository} the composition root wires in.
 */
class BenchmarksServiceTest {

    @Test
    void reportDelegatesToTheRepositoryAndReturnsItsResultUnchanged() {
        BenchmarkReport expected = new BenchmarkReport(
                new BenchmarkHarness("cpu-x", 4, 8_000_000_000L, "jdk-x", "os-x", "2026-01-01T00:00:00Z"),
                List.of(), List.of());
        BenchmarksService service = new BenchmarksService(() -> expected);

        assertThat(service.report()).isSameAs(expected);
    }
}
