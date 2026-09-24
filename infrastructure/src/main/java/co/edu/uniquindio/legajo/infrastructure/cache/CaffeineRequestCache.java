package co.edu.uniquindio.legajo.infrastructure.cache;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Objects;
import java.util.Optional;

/**
 * Caffeine-backed {@link RequestCache}: runtime caches are Caffeine regions with no TTL,
 * because the corpus is static, so nothing here ever expires or is evicted by time; the
 * corpus's 20 documents and six algorithms bound the key space to a few thousand entries at
 * most, so no size-based eviction is needed either). One instance is one region: the
 * bootstrap {@code @Configuration} that wires {@code SimilarityService}/
 * {@code ClusteringService} creates a separate bean per cached computation, each with its
 * own {@link Cache}.
 */
public final class CaffeineRequestCache<K, V> implements RequestCache<K, V> {

    private final Cache<K, V> delegate;

    public CaffeineRequestCache() {
        this.delegate = Caffeine.newBuilder().build();
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(delegate.getIfPresent(key));
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        delegate.put(key, value);
    }
}
