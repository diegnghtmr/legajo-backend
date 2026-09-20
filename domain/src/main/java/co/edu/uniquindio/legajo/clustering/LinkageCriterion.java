package co.edu.uniquindio.legajo.clustering;

/**
 * One of RF2's four hierarchical-clustering linkage criteria (TRD §6.4), hand-written under
 * R-02. Each permitted class contributes only its own {@link LanceWilliamsCoefficients} for
 * a given triple of cluster sizes ({@code sizeI}, {@code sizeJ} for the two clusters about
 * to merge, {@code sizeK} for the third cluster whose distance is being updated); it never
 * builds, holds, or otherwise touches a {@link DistanceMatrix} itself — {@link
 * LanceWilliamsEngine} (R3) is the single place that owns the merge loop and the distance
 * bookkeeping (TRD §6.4, "cada criterio es su propia clase permit sealed ... y aporta sus
 * coeficientes; el motor solo comparte el bucle de fusión").
 *
 * <p><b>Uniform signature (R2 design note).</b> {@link SingleLinkage} and
 * {@link CompleteLinkage} ignore all three sizes (their coefficients are fixed constants);
 * {@link AverageLinkage} uses only {@code sizeI}/{@code sizeJ}; {@link WardLinkage} uses all
 * three. The method still always takes all three, so {@link LanceWilliamsEngine} can call
 * every criterion through one shape without branching on which one it holds.
 *
 * <p><b>Declaration order is fixed</b> (TRD §6.5, "regla de ordenación"): single, complete,
 * average, ward. The ranking rule's final tie-break (R9, out of this feature's scope) reads
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
     * The Lance-Williams coefficients (TRD §6.4 table) for merging the clusters of size
     * {@code sizeI} and {@code sizeJ}, updating the distance to a third cluster of size
     * {@code sizeK}.
     */
    LanceWilliamsCoefficients coefficients(int sizeI, int sizeJ, int sizeK);
}
