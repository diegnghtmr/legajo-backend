package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LanceWilliamsEngine;
import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.WardLinkage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * Pearson correlation between a tree's cophenetic distances and D (TRD §6.5), RF2's
 * <b>primary</b> ranking signal. {@link CopheneticCorrelation} cannot take an arbitrary
 * {@code double[][]} as its D operand — {@link DistanceMatrix}'s constructor is
 * package-private to {@code clustering} (TRD §13) — so every fixture here is built through
 * {@link DistanceMatrix#cosineDistance(List)}, the same toy unit vectors
 * {@code WardLinkageMandatoryTest} uses: v0=(1,0), v1=(0,1), v2=(-1,0), giving
 * D=[[0,1,2],[1,0,1],[2,1,0]].
 *
 * <p><b>Golden value, hand-derived independently of the implementation.</b> Complete
 * linkage on this D: step 1 ties at 1 between (0,1) and (1,2); lexicographically (0,1) wins
 * -&gt; cluster 3 (height 1). New distance(3,2) = max(d(0,2), d(1,2)) = max(2,1) = 2. Step 2
 * merges (2,3) at height 2. Cophenetic pairs: (0,1)=1 (row 0), (0,2)=(1,2)=2 (row 1). D
 * pairs: (0,1)=1, (0,2)=2, (1,2)=1. With n=3 pairs, sumD=4, sumC=5, sumDC=1*1+2*2+1*2=7,
 * sumD2=1+4+1=6, sumC2=1+4+4=9: r = (3*7-4*5) / sqrt((3*6-16)(3*9-25)) = 1/sqrt(4) = 0.5
 * exactly — no floating-point rounding to worry about.
 */
class CopheneticCorrelationTest {

    private static final double TOLERANCE = 1e-9;

    private static final List<List<Double>> TOY_UNIT_VECTORS = List.of(
            List.of(1.0, 0.0),
            List.of(0.0, 1.0),
            List.of(-1.0, 0.0));

    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();

    @Test
    void matchesTheHandComputedGoldenValue() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS);
        LinkageMatrix completeLinkage = engine.agglomerate(d, new CompleteLinkage());

        double r = CopheneticCorrelation.of(completeLinkage, d);

        assertThat(r).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void isInvariantToWardsTwoTimesScale() {
        // TRD §6.5: "La correlación de Pearson es invariante a la escala 2x de Ward, de modo
        // que los cuatro enlaces siguen siendo comparables." Ward always clusters on
        // D_w = 2*D (TRD §6.4); this proves that scaling both the tree's heights and the
        // reference distances by the same factor leaves the correlation unchanged, which is
        // exactly what makes Ward's cophenetic value comparable to the other three linkages'
        // (all computed against their own, unscaled D).
        DistanceMatrix d = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS);
        DistanceMatrix dDoubled = d.wardBase();

        LinkageMatrix onD = engine.agglomerate(d, new WardLinkage());
        LinkageMatrix onDoubledD = engine.agglomerate(dDoubled, new WardLinkage());

        double correlationOnD = CopheneticCorrelation.of(onD, d);
        double correlationOnDoubledD = CopheneticCorrelation.of(onDoubledD, dDoubled);

        assertThat(correlationOnDoubledD).isCloseTo(correlationOnD, within(TOLERANCE));
    }

    @Test
    void rejectsALinkageMatrixWhoseSizeDoesNotMatchTheDistanceMatrix() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS);
        LinkageMatrix linkageForADifferentN = new LinkageMatrix(List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> CopheneticCorrelation.of(linkageForADifferentN, d));
    }

    @Test
    void rejectsANullLinkageMatrix() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS);

        assertThatNullPointerException().isThrownBy(() -> CopheneticCorrelation.of(null, d));
    }

    @Test
    void rejectsANullDistanceMatrix() {
        LinkageMatrix linkage = engine.agglomerate(DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS), new CompleteLinkage());

        assertThatNullPointerException().isThrownBy(() -> CopheneticCorrelation.of(linkage, null));
    }
}
