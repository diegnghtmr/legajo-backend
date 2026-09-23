package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;

import java.util.List;
import java.util.Objects;

/**
 * Wire shape of {@code GET /api/v1/benchmarks} (TRD §6.6, fixed by TRD 1.3.10): {@code
 * harness}, {@code results[]}, and {@code slopes[]}, read as-is from the versioned CSV
 * exports and served with no recalculation.
 */
public record BenchmarkReportResponse(
        BenchmarkHarnessResponse harness, List<BenchmarkResultResponse> results, List<BenchmarkSlopeResponse> slopes) {

    public BenchmarkReportResponse {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(slopes, "slopes");
        results = List.copyOf(results);
        slopes = List.copyOf(slopes);
    }

    public static BenchmarkReportResponse from(BenchmarkReport report) {
        Objects.requireNonNull(report, "report");
        return new BenchmarkReportResponse(
                BenchmarkHarnessResponse.from(report.harness()),
                report.results().stream().map(BenchmarkResultResponse::from).toList(),
                report.slopes().stream().map(BenchmarkSlopeResponse::from).toList());
    }
}
