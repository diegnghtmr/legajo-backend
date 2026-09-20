package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.LinkageCriterion;
import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * One linkage criterion's evaluation summary (TRD §6.5): the cophenetic correlation (the
 * primary ranking signal, measured over the whole tree against D) plus the two
 * partition-quality metrics at the reference cut {@code k_ref} ({@link MeanSilhouette} and
 * {@link DaviesBouldin}). {@link ClusteringRanking} is this record's only intended producer,
 * but — the house rule this package's other validated value types already follow — it
 * re-validates its own invariants rather than trusting its caller.
 *
 * <p>{@code daviesBouldinAtKRef} is an {@link OptionalDouble} because TRD §6.5 fixes an
 * explicit undefined case (coincident centroids) that must never collapse into {@code NaN}
 * or a sentinel double; see {@link DaviesBouldin}'s class Javadoc.
 */
public record LinkageEvaluation(
        LinkageCriterion criterion,
        double copheneticCorrelation,
        double meanSilhouetteAtKRef,
        OptionalDouble daviesBouldinAtKRef) {

    private static final double RANGE_TOLERANCE = 1e-9;

    public LinkageEvaluation {
        Objects.requireNonNull(criterion, "criterion");

        NumericGuards.requireFinite(copheneticCorrelation, "copheneticCorrelation");
        if (NumericGuards.isOutOfRange(copheneticCorrelation, -1.0, 1.0, RANGE_TOLERANCE)) {
            throw new IllegalArgumentException(
                    "copheneticCorrelation must be in [-1, 1], was " + copheneticCorrelation);
        }

        NumericGuards.requireFinite(meanSilhouetteAtKRef, "meanSilhouetteAtKRef");
        if (NumericGuards.isOutOfRange(meanSilhouetteAtKRef, -1.0, 1.0, RANGE_TOLERANCE)) {
            throw new IllegalArgumentException(
                    "meanSilhouetteAtKRef must be in [-1, 1], was " + meanSilhouetteAtKRef);
        }

        Objects.requireNonNull(daviesBouldinAtKRef, "daviesBouldinAtKRef");
        // A defined Davies-Bouldin is a mean of non-negative Euclidean-distance ratios
        // (TRD §6.5), so it can never legitimately be negative.
        daviesBouldinAtKRef.ifPresent(value -> NumericGuards.requireNonNegativeFinite(value, "daviesBouldinAtKRef"));
    }
}
