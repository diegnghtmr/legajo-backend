package co.edu.uniquindio.legajo.infrastructure.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkHarness;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Like {@code MemoizingEmbeddingRepository},
 * this decorator lets {@code BenchmarksStartupValidator}'s boot-time {@code load()} call and
 * every later {@code GET /api/v1/benchmarks} request share one already-parsed {@link
 * BenchmarkReport} instead of re-reading and re-parsing the versioned CSVs on every call —
 * the report never changes for the lifetime of the process (the CSVs are static deployment
 * data, never rewritten while the server runs).
 */
class MemoizingBenchmarkReportRepositoryTest {

    @Test
    void loadDelegatesOnlyOnceAndReturnsTheSameReportInstanceOnSubsequentCalls() {
        CountingBenchmarkReportRepository delegate = new CountingBenchmarkReportRepository(sampleReport());
        MemoizingBenchmarkReportRepository repository = new MemoizingBenchmarkReportRepository(delegate);

        BenchmarkReport first = repository.load();
        BenchmarkReport second = repository.load();
        BenchmarkReport third = repository.load();

        assertThat(delegate.loadCalls.get()).isEqualTo(1);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
    }

    /** A failed load is not memoized: the next call tries the delegate again. */
    @Test
    void aFailedLoadIsRetriedOnTheNextCall() {
        BenchmarkReport report = sampleReport();
        AtomicInteger calls = new AtomicInteger();
        BenchmarkReportRepository flaky = () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first read fails");
            }
            return report;
        };
        MemoizingBenchmarkReportRepository repository = new MemoizingBenchmarkReportRepository(flaky);

        assertThatIllegalStateException().isThrownBy(repository::load);
        assertThat(repository.load()).isSameAs(report);
        assertThat(calls.get()).isEqualTo(2);
    }

    private static BenchmarkReport sampleReport() {
        return new BenchmarkReport(
                new BenchmarkHarness("cpu-x", 4, 8_000_000_000L, "jdk-x", "os-x", "2026-01-01T00:00:00Z"),
                List.of(), List.of());
    }

    /** Hand-written fake (no mocking framework in this module) that counts {@code load()} calls. */
    private static final class CountingBenchmarkReportRepository implements BenchmarkReportRepository {

        private final BenchmarkReport reportToReturn;
        private final AtomicInteger loadCalls = new AtomicInteger();

        private CountingBenchmarkReportRepository(BenchmarkReport reportToReturn) {
            this.reportToReturn = reportToReturn;
        }

        @Override
        public BenchmarkReport load() {
            loadCalls.incrementAndGet();
            return reportToReturn;
        }
    }
}
