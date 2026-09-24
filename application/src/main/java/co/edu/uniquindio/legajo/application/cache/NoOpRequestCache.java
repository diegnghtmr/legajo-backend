package co.edu.uniquindio.legajo.application.cache;

import java.util.Objects;
import java.util.Optional;

/**
 * Always a miss, never stores anything: the similarity/clustering benchmarks run with the
 * similarity cache disabled. Constructor-injectable in place of
 * {@code CaffeineRequestCache}, with no new configuration key — a caller cannot tell whether
 * caching is disabled by this class or absent for another reason, which is exactly the point:
 * every call reports a fresh computation, deterministically.
 */
public final class NoOpRequestCache<K, V> implements RequestCache<K, V> {

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key");
        return Optional.empty();
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        // Deliberately does not store: this cache never reports a hit (see class Javadoc).
    }
}
