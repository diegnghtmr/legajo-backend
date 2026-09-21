package co.edu.uniquindio.legajo.application.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A plain {@link HashMap}-backed {@link RequestCache} test double: no Caffeine, no eviction,
 * no TTL — just enough to prove {@code SimilarityService}/{@code ClusteringService}'s
 * orchestration around the port (hit/miss, what gets stored) without pulling
 * {@code infrastructure} (and Caffeine) onto this module's test classpath, which would cross
 * the hexagonal boundary ArchUnit enforces (this module depends on {@code :domain} only).
 */
public final class FakeRequestCache<K, V> implements RequestCache<K, V> {

    private final Map<K, V> store = new HashMap<>();

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        store.put(key, value);
    }

    /** The number of entries stored so far, for tests asserting a miss really computed once. */
    public int size() {
        return store.size();
    }
}
