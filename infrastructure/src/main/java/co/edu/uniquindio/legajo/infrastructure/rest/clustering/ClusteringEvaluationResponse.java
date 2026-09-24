package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.ClusteringEvaluationBlock;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Wire shape of the {@code evaluation} block within {@code POST /clustering} and
 * {@code POST /clustering/evaluation}: {@code cophenetic} plus
 * {@code meanSilhouette}/{@code daviesBouldin}, each keyed by the fixed cut
 * {@code k ∈ {2,3,4,5} ∩ [2, n-1]}. JSON object keys are always strings, so a
 * {@code Map<Integer, ...>} serializes with string keys ({@code "2"}, {@code "3"}, ...) —
 * there is no integer-keyed JSON object, so this is the direct, not an invented, mapping.
 *
 * <p><b>{@code daviesBouldin}'s value must serialize as JSON {@code null}, never
 * {@code NaN} or a dropped key — the fixed rule for the coincident-centroids case.</b> The domain
 * models this as {@link OptionalDouble} per {@code k}; {@link Map#copyOf} and
 * {@link Map#of} both reject {@code null} values outright, so this class deliberately does
 * <em>not</em> defensively copy the {@code daviesBouldin} map the way {@code meanSilhouette}
 * is copied — an {@link java.util.LinkedHashMap} explicitly allows {@code null} values, and
 * {@code from} is this record's only intended producer.
 */
public record ClusteringEvaluationResponse(
        double cophenetic, Map<Integer, Double> meanSilhouette, Map<Integer, @Nullable Double> daviesBouldin) {

    public static ClusteringEvaluationResponse from(ClusteringEvaluationBlock block) {
        Objects.requireNonNull(block, "block");

        Map<Integer, Double> daviesBouldin = new LinkedHashMap<>();
        for (Map.Entry<Integer, OptionalDouble> entry : block.daviesBouldinByK().entrySet()) {
            daviesBouldin.put(entry.getKey(), entry.getValue().isPresent() ? entry.getValue().getAsDouble() : null);
        }

        return new ClusteringEvaluationResponse(block.copheneticCorrelation(), Map.copyOf(block.meanSilhouetteByK()),
                Collections.unmodifiableMap(daviesBouldin));
    }
}
