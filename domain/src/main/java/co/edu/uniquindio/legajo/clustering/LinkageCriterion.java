package co.edu.uniquindio.legajo.clustering;

/**
 * One of the four hierarchical-clustering linkage criteria, hand-written; no library
 * implements it. Each permitted class contributes only its own {@link LanceWilliamsCoefficients} for
 * a given triple of cluster sizes ({@code sizeI}, {@code sizeJ} for the two clusters about
 * to merge, {@code sizeK} for the third cluster whose distance is being updated); it never
 * builds, holds, or otherwise touches a {@link DistanceMatrix} itself — {@link
 * LanceWilliamsEngine} is the single place that owns the merge loop and the distance
 * bookkeeping: each criterion is its own sealed-permitted class that only contributes its
 * coefficients; the engine alone shares the merge loop.
 *
 * <p><b>Uniform signature (design note).</b> {@link SingleLinkage} and
 * {@link CompleteLinkage} ignore all three sizes (their coefficients are fixed constants);
 * {@link AverageLinkage} uses only {@code sizeI}/{@code sizeJ}; {@link WardLinkage} uses all
 * three. The method still always takes all three, so {@link LanceWilliamsEngine} can call
 * every criterion through one shape without branching on which one it holds.
 *
 * <p><b>Declaration order is fixed</b>: single, complete,
 * average, ward. The ranking rule's final tie-break (out of this class's scope) reads
 * that declaration order directly from this {@code permits} list, so the order below must
 * never be reshuffled.
 */
public sealed interface LinkageCriterion
        permits SingleLinkage, CompleteLinkage, AverageLinkage, WardLinkage {

    /** Stable identifier, mirroring {@link co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm#id()}. */
    String id();

    /** Human-readable label for the UI. */
    String displayName();

    /**
     * The Lance-Williams coefficients for merging the clusters of size
     * {@code sizeI} and {@code sizeJ}, updating the distance to a third cluster of size
     * {@code sizeK}.
     */
    LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK);
}
