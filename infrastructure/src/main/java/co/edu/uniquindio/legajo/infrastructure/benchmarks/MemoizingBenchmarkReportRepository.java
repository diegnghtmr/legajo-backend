package co.edu.uniquindio.legajo.infrastructure.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;

import java.util.Objects;

/**
 * Decorator over another {@link BenchmarkReportRepository} that loads the underlying CSV
 * export at most once per process, then reuses that same {@link BenchmarkReport} instance on
 * every later {@link #load()} call — the same shape as {@code MemoizingEmbeddingRepository},
 * for the same reason: {@code DomainConfiguration}'s startup validator
 * calls {@code load()} once at boot, and every {@code GET /api/v1/benchmarks} request calls
 * it again; without this decorator both would re-read and re-parse
 * {@code benchmarks/results/jmh-results.csv}/{@code slopes.csv} from disk every time, even
 * though the versioned CSVs never change while the server runs. Uses the same
 * double-checked-locking shape as {@code MemoizingEmbeddingRepository}: a {@code volatile}
 * field checked without a lock on the common (already memoized) path, and a {@code
 * synchronized} block only around the first, uncached call. A failed load is not memoized —
 * the next call retries the delegate, matching {@code MemoizingEmbeddingRepository}'s own
 * fail-open-to-retry behavior for a transient read failure.
 */
public final class MemoizingBenchmarkReportRepository implements BenchmarkReportRepository {

    private final BenchmarkReportRepository delegate;

    private volatile BenchmarkReport cachedValue;

    public MemoizingBenchmarkReportRepository(BenchmarkReportRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public BenchmarkReport load() {
        BenchmarkReport existing = cachedValue;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cachedValue == null) {
                cachedValue = delegate.load();
            }
            return cachedValue;
        }
    }
}
