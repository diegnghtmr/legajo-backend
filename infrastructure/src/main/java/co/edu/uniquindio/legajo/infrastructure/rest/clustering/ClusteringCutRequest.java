package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import org.jspecify.annotations.Nullable;

/**
 * Request body of {@code POST /api/v1/clustering/cut}: {@code representation}
 * optional (defaults to {@code tfidf-cosine}), {@code linkage} required, {@code k} required
 * — the only clustering endpoint that accepts a free cut. {@code k} is boxed ({@code
 * Integer}, not {@code int}) so a missing field deserializes to {@code null} instead of
 * silently defaulting to {@code 0} and failing range validation with a confusing message;
 * {@code ClusteringController} checks for {@code null} explicitly before unboxing.
 */
public record ClusteringCutRequest(@Nullable String representation, @Nullable String linkage, @Nullable Integer k) {
}
