package co.edu.uniquindio.legajo.application.cache;

import java.util.Optional;

/**
 * A generic request-keyed cache abstraction (TRD §9: "runtime caches are Caffeine regions,
 * no TTL" — the corpus is static). Framework-free by design (task A5, feature doc {@code
 * rest-api.md}): {@code SimilarityService}/{@code ClusteringService} (application layer)
 * depend on this port, never on Caffeine or Spring directly, so ArchUnit's layering rule
 * stays satisfied without pulling either dependency into this module. {@code infrastructure}
 * supplies the real Caffeine-backed implementation ({@code CaffeineRequestCache}); this
 * module also supplies {@link NoOpRequestCache} so NFR-QA-01's "cache disabled" benchmark
 * case and tests that must observe every call as fresh can run without it.
 *
 * <p>Deliberately {@code get}/{@code put} rather than a single opaque
 * compute-if-absent-style method: the caller needs to know whether a lookup was a hit or a
 * miss to set the wire-facing {@code cached} flag itself (TRD §6.6), which a single method
 * that always returns a value would hide.
 */
public interface RequestCache<K, V> {

    /** The cached value for {@code key}, or empty if nothing has been {@link #put} for it yet. */
    Optional<V> get(K key);

    /** Stores {@code value} under {@code key}, replacing whatever was cached there before. */
    void put(K key, V value);
}
