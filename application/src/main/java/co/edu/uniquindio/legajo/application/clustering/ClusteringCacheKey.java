package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.similarity.Representation;

import java.util.Objects;

/**
 * Cache key for one linkage's full computation (TRD §9, task A5): {@code (representation,
 * linkageId)}. {@code run}, {@code evaluateOnly} (which delegates to {@code run}) and
 * {@code cut} all share the same cache under this key, so a linkage computed once by any of
 * the three endpoints is never recomputed by another — the statelessness rule (TRD §6.6)
 * only forbids a result depending on a previous *different* request, never forbids reusing
 * an already-computed, request-keyed result for the exact same one.
 */
public record ClusteringCacheKey(Representation representation, String linkageId) {

    public ClusteringCacheKey {
        Objects.requireNonNull(representation, "representation");
        Objects.requireNonNull(linkageId, "linkageId");
    }
}
