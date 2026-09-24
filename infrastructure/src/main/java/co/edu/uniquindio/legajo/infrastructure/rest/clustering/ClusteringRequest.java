package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Request body shared by {@code POST /api/v1/clustering} and
 * {@code POST /api/v1/clustering/evaluation}: {@code representation} optional,
 * defaulting to {@code tfidf-cosine}; {@code linkages} optional, defaulting to all four.
 *
 * <p><b>No {@code ks} field, by construction.</b> There is deliberately no request
 * parameter {@code ks}, so that no conforming request can alter the fixed
 * evaluation cuts {@code k ∈ {2,3,4,5} ∩ [2, n-1]}. This record simply has no such field:
 * Jackson either drops an unrecognized {@code ks} property or (Spring Boot's actual
 * default, proven by {@code ClusteringControllerTest
 * .anUnknownKsFieldNeverChangesTheFixedEvaluationCuts}) rejects the whole request as
 * malformed — either outcome makes the rule structural rather than merely documented, since
 * there is no code path in this record, {@code ClusteringController}, or {@code
 * ClusteringService} that ever reads a caller-supplied {@code k} set.
 */
public record ClusteringRequest(@Nullable String representation, @Nullable List<String> linkages) {
}
