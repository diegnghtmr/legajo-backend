package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAC-13 follow-up (feature doc {@code rest-followups.md}, F3): {@link
 * MemoizingEmbeddingRepository} lets the startup validator's {@code load()} call and every
 * later request-time {@code load()} call (from {@code SimilarityService}/{@code
 * ClusteringService}/{@code EmbeddingsService}) share one already-validated, already-parsed
 * {@link EmbeddingCache} instead of re-reading and re-validating the underlying cache file on
 * every call.
 */
class MemoizingEmbeddingRepositoryTest {

    @Test
    void loadDelegatesOnlyOnceAndReturnsTheSameCacheInstanceOnSubsequentCalls() {
        CountingEmbeddingRepository delegate = new CountingEmbeddingRepository(twoVectorCache());
        MemoizingEmbeddingRepository repository = new MemoizingEmbeddingRepository(delegate);

        EmbeddingCache first = repository.load();
        EmbeddingCache second = repository.load();
        EmbeddingCache third = repository.load();

        assertThat(delegate.loadCalls.get()).isEqualTo(1);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
    }

    @Test
    void saveIsPropagatedToTheDelegateOnEveryCall() {
        CountingEmbeddingRepository delegate = new CountingEmbeddingRepository(twoVectorCache());
        MemoizingEmbeddingRepository repository = new MemoizingEmbeddingRepository(delegate);
        EmbeddingCache cacheToSave = twoVectorCache();

        repository.save(cacheToSave);
        repository.save(cacheToSave);

        assertThat(delegate.savedCaches).containsExactly(cacheToSave, cacheToSave);
    }

    private static EmbeddingCache twoVectorCache() {
        return new EmbeddingCache("1.0", "1.0", "corpus-hash-abc", "all-MiniLM-L6-v2", 2,
                List.of(
                        new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 1.0, List.of(0.6, 0.8)),
                        new EmbeddingVector("d02", "local", "all-MiniLM-L6-v2", 1.0, List.of(1.0, 0.0))));
    }

    /** Hand-written fake (no mocking framework in this module) that counts {@code load()} calls. */
    private static final class CountingEmbeddingRepository implements EmbeddingRepository {

        private final EmbeddingCache cacheToReturn;
        private final AtomicInteger loadCalls = new AtomicInteger();
        private final List<EmbeddingCache> savedCaches = new ArrayList<>();

        private CountingEmbeddingRepository(EmbeddingCache cacheToReturn) {
            this.cacheToReturn = cacheToReturn;
        }

        @Override
        public EmbeddingCache load() {
            loadCalls.incrementAndGet();
            return cacheToReturn;
        }

        @Override
        public void save(EmbeddingCache cache) {
            savedCaches.add(cache);
        }
    }
}
