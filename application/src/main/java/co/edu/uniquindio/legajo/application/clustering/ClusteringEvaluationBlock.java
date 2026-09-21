package co.edu.uniquindio.legajo.application.clustering;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * The evaluation block one linkage carries in {@code POST /clustering} and
 * {@code POST /clustering/evaluation} (TRD §6.6): the cophenetic correlation over the whole
 * tree, plus mean silhouette and Davies-Bouldin at every fixed cut
 * {@code k ∈ {2,3,4,5} ∩ [2, n-1]} (TAC-04). Davies-Bouldin is an {@link OptionalDouble} per
 * {@code k} because TRD §6.5 fixes an explicit undefined case (coincident centroids) that
 * must surface as {@code null} on the wire, never {@code NaN} or a sentinel.
 *
 * <p>{@code meanSilhouetteByK}/{@code daviesBouldinByK} share exactly the fixed k set this
 * linkage was evaluated at; both are keyed the same way so a caller never has to reconcile
 * two different key sets.
 */
public record ClusteringEvaluationBlock(
        double copheneticCorrelation,
        Map<Integer, Double> meanSilhouetteByK,
        Map<Integer, OptionalDouble> daviesBouldinByK) {

    public ClusteringEvaluationBlock {
        Objects.requireNonNull(meanSilhouetteByK, "meanSilhouetteByK");
        Objects.requireNonNull(daviesBouldinByK, "daviesBouldinByK");
        meanSilhouetteByK = Map.copyOf(meanSilhouetteByK);
        daviesBouldinByK = Map.copyOf(daviesBouldinByK);
        if (!meanSilhouetteByK.keySet().equals(daviesBouldinByK.keySet())) {
            throw new IllegalArgumentException(
                    "meanSilhouetteByK and daviesBouldinByK must share the same fixed-k set, were "
                            + meanSilhouetteByK.keySet() + " and " + daviesBouldinByK.keySet());
        }
    }
}
