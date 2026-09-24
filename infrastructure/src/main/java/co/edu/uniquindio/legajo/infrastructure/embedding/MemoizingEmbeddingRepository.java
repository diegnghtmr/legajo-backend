package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;

import java.util.Objects;

/**
 * Decorator over another {@link EmbeddingRepository} that loads and validates the underlying
 * cache at most once per process, then reuses that same {@link EmbeddingCache} instance on
 * every later {@link #load()} call.
 *
 * <p><b>Why.</b> {@code SimilarityService}, {@code ClusteringService}, and {@code
 * EmbeddingsService} each call {@code EmbeddingRepository#load()} on every request that needs
 * an embedding cache; {@link JsonEmbeddingRepository#load()} re-reads and re-parses the cache
 * file and re-validates its {@code corpusSha256} on every single call. {@code
 * DomainConfiguration} now also validates each cache once at application startup
 * — without this decorator, that startup call would just be one more full re-read on top of
 * the one every request already pays for. Wrapping the bean in this class instead makes the
 * startup call and every subsequent request-time call share the exact same parsed,
 * already-validated cache, so the file is read and validated exactly once for the lifetime of
 * the process.
 *
 * <p>Uses the same double-checked-locking shape as {@code LiveApiEmbeddingRepository}'s lazy
 * client construction: a {@code volatile} field checked without a lock on the common (already
 * memoized) path, and a {@code synchronized} block only around the first, uncached call.
 *
 * <p>{@link #save(EmbeddingCache)} is not memoized: it always delegates straight through,
 * since nothing at request time writes a cache (only the offline precompute CLIs call {@code
 * save}, directly against a plain {@link JsonEmbeddingRepository}, never through this
 * decorator).
 */
public final class MemoizingEmbeddingRepository implements EmbeddingRepository {

    private final EmbeddingRepository delegate;

    private volatile EmbeddingCache cachedValue;

    public MemoizingEmbeddingRepository(EmbeddingRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public EmbeddingCache load() {
        EmbeddingCache existing = cachedValue;
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

    @Override
    public synchronized void save(EmbeddingCache cache) {
        delegate.save(cache);
        // Keep later loads consistent with what was just written.
        cachedValue = cache;
    }
}
