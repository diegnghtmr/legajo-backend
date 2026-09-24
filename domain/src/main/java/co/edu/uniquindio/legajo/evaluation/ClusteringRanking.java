package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.AverageLinkage;
import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.LinkageCriterion;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.clustering.WardLinkage;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The ranking rule between the four linkage criteria, fixed as a three-step procedure:
 * (1) every linkage is evaluated on the three metrics; (2)
 * the linkage with the highest cophenetic correlation is, by default, the reported "most
 * coherent grouping" — the tie set is every linkage whose correlation is within
 * {@value #TIE_TOLERANCE} of the highest, resolved by highest mean silhouette at
 * {@code k_ref}, then lowest Davies-Bouldin at {@code k_ref}, then declaration order (single,
 * complete, average, ward); (3) if the plain silhouette leader at {@code k_ref} differs from
 * that resolved winner, both are reported (the tree's best cophenetic fit versus the best
 * partition at {@code k_ref}).
 *
 * <p><b>Two tie-break rules not otherwise specified (author decisions).</b> First, when the
 * cophenetic tie set's silhouette-then-DB tie-break reaches an
 * evaluation with an undefined ("null") Davies-Bouldin, it is treated as worse than any
 * defined finite value for that step — an undefined DB carries no evidence of a
 * well-separated partition, so it cannot win a comparison against one that does. Second, the
 * plain silhouette leader at {@code k_ref} (step 3, computed over all four linkages, not just
 * the cophenetic tie set) has no tie-break rule of its own specified elsewhere; this class
 * falls back to the same declaration order used in step 2, for the same determinism reason.
 */
public final class ClusteringRanking {

    private static final double TIE_TOLERANCE = 1e-3;

    private ClusteringRanking() {
    }

    /**
     * Applies the ranking rule to exactly the four fixed linkages' evaluations
     * (single, complete, average, ward — no more, no fewer, no duplicates).
     * {@code sampleSize} is {@code n = |corpus|}, carried through for the sample-size caveat
     * that must always be reported alongside the ranking.
     */
    public static ClusteringRankingResult of(List<LinkageEvaluation> evaluations, int sampleSize) {
        Objects.requireNonNull(evaluations, "evaluations");
        List<LinkageEvaluation> fixed = List.copyOf(evaluations);
        if (fixed.size() != 4) {
            throw new IllegalArgumentException(
                    "the ranking rule requires exactly the four fixed linkages, was "
                            + fixed.size());
        }
        boolean[] seen = new boolean[4];
        for (LinkageEvaluation evaluation : fixed) {
            int index = declarationOrder(evaluation.criterion());
            if (seen[index]) {
                throw new IllegalArgumentException(
                        "duplicate linkage criterion in evaluations: " + evaluation.criterion().id());
            }
            seen[index] = true;
        }
        if (sampleSize < 1) {
            throw new IllegalArgumentException("sampleSize must be at least 1, was " + sampleSize);
        }

        double maxCorrelation = fixed.stream()
                .mapToDouble(LinkageEvaluation::copheneticCorrelation)
                .max()
                .orElseThrow();
        List<LinkageEvaluation> tieSet = fixed.stream()
                .filter(evaluation -> maxCorrelation - evaluation.copheneticCorrelation() <= TIE_TOLERANCE)
                .toList();

        Comparator<LinkageEvaluation> copheneticTieBreak = Comparator
                .comparingDouble((LinkageEvaluation evaluation) -> -evaluation.meanSilhouetteAtKRef())
                .thenComparingDouble(evaluation -> evaluation.daviesBouldinAtKRef().orElse(Double.POSITIVE_INFINITY))
                .thenComparingInt(evaluation -> declarationOrder(evaluation.criterion()));
        LinkageEvaluation bestTreeFidelity = tieSet.stream().min(copheneticTieBreak).orElseThrow();

        Comparator<LinkageEvaluation> silhouetteOrder = Comparator
                .comparingDouble((LinkageEvaluation evaluation) -> -evaluation.meanSilhouetteAtKRef())
                .thenComparingInt(evaluation -> declarationOrder(evaluation.criterion()));
        LinkageEvaluation bestPartitionAtKRef = fixed.stream().min(silhouetteOrder).orElseThrow();

        boolean leadersDiffer =
                !bestTreeFidelity.criterion().id().equals(bestPartitionAtKRef.criterion().id());

        return new ClusteringRankingResult(fixed, tieSet, bestTreeFidelity, bestPartitionAtKRef, leadersDiffer, sampleSize);
    }

    /**
     * The fixed declaration order (single, complete, average, ward), read directly from
     * {@link LinkageCriterion}'s {@code permits} list via
     * an exhaustive pattern-matching switch — never from a fragile id-string lookup table
     * that could silently drift from the sealed type's actual permitted set.
     */
    private static int declarationOrder(LinkageCriterion criterion) {
        return switch (criterion) {
            case SingleLinkage ignored -> 0;
            case CompleteLinkage ignored -> 1;
            case AverageLinkage ignored -> 2;
            case WardLinkage ignored -> 3;
        };
    }
}
