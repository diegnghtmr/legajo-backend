package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The non-negotiable mandatory Ward tests: heights over toy
 * unit vectors equal 2 × the ESS increment, and Ward on D versus Ward on 2·D gives an
 * identical merge order with heights in ratio exactly 2. The third mandatory item — "el
 * constructor acepta únicamente D derivada de la representación seleccionada (sin entrada
 * de matriz arbitraria)" — is pinned by {@link LanceWilliamsEngineTest
 * #engineExposesNoWayToAgglomerateOverAnArbitraryMatrix()}: it is a property of the
 * engine's public API surface in general, not specific to {@link WardLinkage}, so it lives
 * with the engine's other structural tests rather than being duplicated here.
 *
 * <p><b>Toy fixture, hand-derived independently of the engine.</b> Three 2D unit vectors:
 * v0=(1,0), v1=(0,1), v2=(-1,0). Cosine distances: cos(v0,v1)=0 -&gt; D01=1;
 * cos(v0,v2)=-1 -&gt; D02=2; cos(v1,v2)=0 -&gt; D12=1. So D = [[0,1,2],[1,0,1],[2,1,0]] and
 * D_w = 2·D = [[0,2,4],[2,0,2],[4,2,0]].
 *
 * <p><b>Merge 1 (on D_w).</b> Distances (0,1) and (1,2) tie at 2; lexicographically
 * smallest is (0,1) -&gt; merge at height 2.0, cluster size 2.
 * <b>ESS increment</b>: two singletons at v0, v1, ΔESS = (n_i·n_j)/(n_i+n_j)·‖c_i-c_j‖²
 * = (1·1)/2·‖(1,0)-(0,1)‖² = 0.5·2 = 1.0. 2·ΔESS = 2.0 = the merge height. (This is not a
 * coincidence restricted to D_w: for two unit-vector singletons, D = ‖u-v‖²/2 = ΔESS
 * exactly, which is exactly why Ward is defined to consume D_w = 2·D — so its
 * heights read directly as 2·ΔESS.)
 *
 * <p><b>Merge 2 (on D_w).</b> Only pair left is (2, cluster3); via Lance-Williams with
 * n_i=n_j=n_k=1, n_T=3, alphaI=alphaJ=2/3, beta=-1/3:
 * d(3,2) = (2/3)·4 + (2/3)·2 - (1/3)·2 = 8/3+4/3-2/3 = 10/3 ≈ 3.3333333333333335.
 * <b>ESS increment</b>: centroid of {v0,v1} is c=(0.5,0.5); merging with v2=(-1,0),
 * n_i=2, n_j=1, ΔESS = (2·1)/3·‖(0.5,0.5)-(-1,0)‖² = (2/3)·(1.5²+0.5²) = (2/3)·2.5 = 5/3.
 * 2·ΔESS = 10/3, matching the recursive Lance-Williams height exactly — this is the
 * non-trivial check: the *recursive* update (not just the first, one-shot merge) still
 * honors the ESS relationship.
 */
class WardLinkageMandatoryTest {

    private static final double TOLERANCE = 1e-9;

    private static final List<List<Double>> TOY_UNIT_VECTORS = List.of(
            List.of(1.0, 0.0),
            List.of(0.0, 1.0),
            List.of(-1.0, 0.0));

    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();

    @Test
    void heightsOverToyUnitVectorsEqualTwiceTheEssIncrement() {
        DistanceMatrix wardBase = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS).wardBase();

        LinkageMatrix matrix = engine.agglomerate(wardBase, new WardLinkage());

        assertThat(matrix.size()).isEqualTo(2);

        // Merge 1: v0=(1,0) with v1=(0,1), both singletons.
        double essIncrement1 = essIncrement(1, 1, List.of(1.0, 0.0), List.of(0.0, 1.0));
        assertThat(matrix.rows().get(0).mergeDistance()).isCloseTo(2 * essIncrement1, within(TOLERANCE));

        // Merge 2: centroid of {v0,v1} = (0.5,0.5), size 2, with v2=(-1,0), size 1.
        double essIncrement2 = essIncrement(2, 1, List.of(0.5, 0.5), List.of(-1.0, 0.0));
        assertThat(matrix.rows().get(1).mergeDistance()).isCloseTo(2 * essIncrement2, within(TOLERANCE));
    }

    @Test
    void wardOnDAndOnDoubledDGiveIdenticalMergeOrderWithHeightsInRatioTwo() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(TOY_UNIT_VECTORS);
        DistanceMatrix dDoubled = d.wardBase();

        LinkageMatrix onD = engine.agglomerate(d, new WardLinkage());
        LinkageMatrix onDoubledD = engine.agglomerate(dDoubled, new WardLinkage());

        assertThat(onD.size()).isEqualTo(onDoubledD.size());
        for (int i = 0; i < onD.size(); i++) {
            LinkageStep fromD = onD.rows().get(i);
            LinkageStep fromDoubledD = onDoubledD.rows().get(i);

            assertThat(fromDoubledD.idx1()).as("row %d idx1", i).isEqualTo(fromD.idx1());
            assertThat(fromDoubledD.idx2()).as("row %d idx2", i).isEqualTo(fromD.idx2());
            assertThat(fromDoubledD.size()).as("row %d size", i).isEqualTo(fromD.size());
            assertThat(fromDoubledD.mergeDistance())
                    .as("row %d height ratio", i)
                    .isCloseTo(2 * fromD.mergeDistance(), within(TOLERANCE));
        }

        // The hand-derived heights themselves (D: 1.0 then 5/3; D_w: 2.0 then 10/3).
        assertThat(onD.rows().get(0).mergeDistance()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(onD.rows().get(1).mergeDistance()).isCloseTo(5.0 / 3.0, within(TOLERANCE));
        assertThat(onDoubledD.rows().get(0).mergeDistance()).isCloseTo(2.0, within(TOLERANCE));
        assertThat(onDoubledD.rows().get(1).mergeDistance()).isCloseTo(10.0 / 3.0, within(TOLERANCE));
    }

    /** ΔESS = (n_i·n_j)/(n_i+n_j) · ‖centroidI - centroidJ‖², the Ward objective's increment. */
    private static double essIncrement(int sizeI, int sizeJ, List<Double> centroidI, List<Double> centroidJ) {
        double squaredDistance = 0.0;
        for (int d = 0; d < centroidI.size(); d++) {
            double diff = centroidI.get(d) - centroidJ.get(d);
            squaredDistance += diff * diff;
        }
        return ((double) (sizeI * sizeJ) / (sizeI + sizeJ)) * squaredDistance;
    }
}
