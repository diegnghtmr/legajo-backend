package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * The naive Lance-Williams agglomeration loop: repeatedly merges the pair with
 * the smallest current distance, recomputes distances to every other active cluster via
 * {@link LinkageCriterion#coefficients(int, int, int)}, and records one {@link LinkageStep}
 * per merge until a single cluster remains. Deliberately O(n^3) time / O(n^2) space
 * (SLINK/CLINK are explicit non-goals) — clarity over micro-optimization at the
 * n=20 reference corpus scale.
 *
 * <p><b>n=5 golden fixture, hand-derived independently of this class.</b> Five points on a
 * real line at positions 0, 1, 2, 10, 11, with D(i,j) = |p_i - p_j| (this is a valid
 * symmetric, non-negative, zero-diagonal distance matrix — it need not be an actual
 * cosine distance to exercise the engine's arithmetic; {@link DistanceMatrixTest} already
 * covers the cosine derivation itself):
 *
 * <pre>
 *        0    1    2    3    4
 *   0    0    1    2    10   11
 *   1    1    0    1    9    10
 *   2    2    1    0    8    9
 *   3    10   9    8    0    1
 *   4    11   10   9    1    0
 * </pre>
 *
 * <p><b>Single linkage</b> (d(ij,k)=min(d(i,k),d(j,k))): step 1 has a three-way tie at
 * distance 1 among (0,1), (1,2), (3,4); the lexicographically smallest pair is (0,1) -&gt;
 * merge into cluster 5 (size 2). New distances: d(5,2)=min(2,1)=1, d(5,3)=min(10,9)=9,
 * d(5,4)=min(11,10)=10; d(2,3)=8, d(2,4)=9, d(3,4)=1 unchanged. Step 2 ties again at 1
 * between (2,5) and (3,4); lexicographically (2,5) &lt; (3,4) -&gt; merge into cluster 6
 * (size 3). New distance: d(6,3)=min(9,8)=8, d(6,4)=min(10,9)=9; d(3,4)=1 unchanged. Step 3
 * merges (3,4) at distance 1 into cluster 7 (size 2). New distance: d(6,7)=min(8,9)=8. Step
 * 4 merges (6,7) at distance 8 into cluster 8 (size 5). Rows: (0,1,1,2), (2,5,1,3),
 * (3,4,1,2), (6,7,8,5) — heights 1,1,1,8, monotone.
 *
 * <p><b>Complete linkage</b> (d(ij,k)=max(d(i,k),d(j,k))): step 1 same three-way tie,
 * lexicographically smallest (0,1) -&gt; cluster 5 (size 2), distance 1. New distances:
 * d(5,2)=max(2,1)=2, d(5,3)=max(10,9)=10, d(5,4)=max(11,10)=11. Step 2: minimum is now (3,4)
 * at 1 -&gt; cluster 6 (size 2). New distances: d(5,6)=max(10,11)=11, d(2,6)=max(8,9)=9.
 * Step 3: minimum is (2,5) at 2 -&gt; cluster 7 (size 3). New distance:
 * d(7,6)=max(11,9)=11. Step 4 merges (6,7) at 11 into cluster 8 (size 5). Rows:
 * (0,1,1,2), (3,4,1,2), (2,5,2,3), (6,7,11,5) — heights 1,1,2,11, monotone.
 *
 * <p><b>Average linkage</b> (d(ij,k)=(n_i*d(i,k)+n_j*d(j,k))/(n_i+n_j)): step 1 merges
 * (0,1) at 1 into cluster 5 (size 2, n_i=n_j=1): d(5,2)=(2+1)/2=1.5, d(5,3)=(10+9)/2=9.5,
 * d(5,4)=(11+10)/2=10.5. Step 2 merges (3,4) at 1 into cluster 6 (size 2, n_i=n_j=1):
 * d(6,5)=(9.5+10.5)/2=10.0, d(6,2)=(8+9)/2=8.5. Step 3 merges (2,5) at 1.5 into cluster 7
 * (size 3, n_i=1 for cluster 2, n_j=2 for cluster 5):
 * d(7,6)=(1*8.5+2*10.0)/3=28.5/3=9.5. Step 4 merges (6,7) at 9.5 into cluster 8 (size 5).
 * Rows: (0,1,1,2), (3,4,1,2), (2,5,1.5,3), (6,7,9.5,5) — heights 1,1,1.5,9.5, monotone.
 *
 * <p><b>Ward linkage</b> (applied directly to this D, not the D_w=2·D base — that
 * specific relationship is pinned by a separate mandatory test): step 1 merges (0,1) at 1 into
 * cluster 5 (n_i=n_j=1, size 2). For each k, n_T=3, alphaI=alphaJ=2/3, beta=-1/3:
 * d(5,2)=(2/3)*2+(2/3)*1-(1/3)*1=5/3, d(5,3)=(2/3)*10+(2/3)*9-(1/3)*1=37/3,
 * d(5,4)=(2/3)*11+(2/3)*10-(1/3)*1=41/3. Step 2 merges (3,4) at 1 into cluster 6 (n_i=n_j=1,
 * size 2). For k=5 (n_k=2, n_T=4, alphaI=alphaJ=3/4, beta=-1/2):
 * d(6,5)=(3/4)*(37/3)+(3/4)*(41/3)-(1/2)*1=37/4+41/4-1/2=19.0. For k=2 (n_k=1, n_T=3,
 * alphaI=alphaJ=2/3, beta=-1/3): d(6,2)=(2/3)*8+(2/3)*9-(1/3)*1=11.0. Step 3 merges (2,5)
 * at 5/3 into cluster 7 (n_i=1 for cluster 2, n_j=2 for cluster 5, size 3). For k=6 (n_k=2,
 * n_T=5, alphaI=3/5, alphaJ=4/5, beta=-2/5):
 * d(7,6)=(3/5)*11.0+(4/5)*19.0-(2/5)*(5/3)=6.6+15.2-2/3=317/15. Step 4 merges (6,7) at
 * 317/15 into cluster 8 (size 5). Rows: (0,1,1,2), (3,4,1,2), (2,5,5/3,3),
 * (6,7,317/15,5) — heights 1, 1, 1.6666..., 21.1333..., monotone.
 */
class LanceWilliamsEngineTest {

    private static final double TOLERANCE = 1e-9;

    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();

    private static DistanceMatrix fiveByFiveLineDistances() {
        return new DistanceMatrix(new double[][] {
                {0, 1, 2, 10, 11},
                {1, 0, 1, 9, 10},
                {2, 1, 0, 8, 9},
                {10, 9, 8, 0, 1},
                {11, 10, 9, 1, 0},
        });
    }

    @Test
    void singleLinkageMatchesTheHandDerivedGolden() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new SingleLinkage());

        assertThat(matrix.size()).isEqualTo(4);
        assertRow(matrix, 0, 0, 1, 1.0, 2);
        assertRow(matrix, 1, 2, 5, 1.0, 3);
        assertRow(matrix, 2, 3, 4, 1.0, 2);
        assertRow(matrix, 3, 6, 7, 8.0, 5);
    }

    @Test
    void completeLinkageMatchesTheHandDerivedGolden() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new CompleteLinkage());

        assertThat(matrix.size()).isEqualTo(4);
        assertRow(matrix, 0, 0, 1, 1.0, 2);
        assertRow(matrix, 1, 3, 4, 1.0, 2);
        assertRow(matrix, 2, 2, 5, 2.0, 3);
        assertRow(matrix, 3, 6, 7, 11.0, 5);
    }

    @Test
    void averageLinkageMatchesTheHandDerivedGolden() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new AverageLinkage());

        assertThat(matrix.size()).isEqualTo(4);
        assertRow(matrix, 0, 0, 1, 1.0, 2);
        assertRow(matrix, 1, 3, 4, 1.0, 2);
        assertRow(matrix, 2, 2, 5, 1.5, 3);
        assertRow(matrix, 3, 6, 7, 9.5, 5);
    }

    @Test
    void wardLinkageMatchesTheHandDerivedGolden() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new WardLinkage());

        assertThat(matrix.size()).isEqualTo(4);
        assertRow(matrix, 0, 0, 1, 1.0, 2);
        assertRow(matrix, 1, 3, 4, 1.0, 2);
        assertRow(matrix, 2, 2, 5, 5.0 / 3.0, 3);
        assertRow(matrix, 3, 6, 7, 317.0 / 15.0, 5);
    }

    @Test
    void tiesAreBrokenByTheLexicographicallySmallestPair() {
        // Hand-built tie, isolated from cluster-size arithmetic: (0,1) and (0,2) both tie
        // at distance 5; every other pair is far away (100). Lexicographically, (0,1) <
        // (0,2) (same idx1, smaller idx2), so (0,1) must merge first regardless of merge
        // order in the loop that scans candidate pairs.
        DistanceMatrix distances = new DistanceMatrix(new double[][] {
                {0, 5, 5, 100},
                {5, 0, 100, 100},
                {5, 100, 0, 100},
                {100, 100, 100, 0},
        });

        LinkageMatrix matrix = engine.agglomerate(distances, new SingleLinkage());

        assertThat(matrix.rows().get(0).idx1()).isEqualTo(0);
        assertThat(matrix.rows().get(0).idx2()).isEqualTo(1);
        assertThat(matrix.rows().get(0).mergeDistance()).isCloseTo(5.0, within(TOLERANCE));
    }

    @Test
    void sameInputTwiceProducesByteIdenticalLinkageMatrices() {
        // Determinism requirement, including tie resolution.
        DistanceMatrix distances = fiveByFiveLineDistances();

        LinkageMatrix first = engine.agglomerate(distances, new AverageLinkage());
        LinkageMatrix second = engine.agglomerate(fiveByFiveLineDistances(), new AverageLinkage());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void everyRowHasIdx1StrictlyLessThanIdx2() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new WardLinkage());

        assertThat(matrix.rows()).allSatisfy(row -> assertThat(row.idx1()).isLessThan(row.idx2()));
    }

    @Test
    void rowCountIsAlwaysNMinusOne() {
        LinkageMatrix matrix = engine.agglomerate(fiveByFiveLineDistances(), new CompleteLinkage());

        assertThat(matrix.size()).isEqualTo(5 - 1);
    }

    @Test
    void agglomerateRejectsANullDistanceMatrix() {
        assertThatNullPointerException()
                .isThrownBy(() -> engine.agglomerate(null, new SingleLinkage()));
    }

    @Test
    void agglomerateRejectsANullCriterion() {
        assertThatNullPointerException()
                .isThrownBy(() -> engine.agglomerate(fiveByFiveLineDistances(), null));
    }

    @Test
    void engineExposesNoWayToAgglomerateOverAnArbitraryMatrix() throws NoSuchMethodException {
        // The fixed rule that the constructor accepts only D derived from the selected
        // representation, never a raw matrix input. DistanceMatrix's own
        // constructor is already package-private; this pins that the engine's public
        // API never reintroduces a bypass by accepting a raw double[][] (or similar) in
        // place of a validated DistanceMatrix.
        Method agglomerate =
                LanceWilliamsEngine.class.getMethod("agglomerate", DistanceMatrix.class, LinkageCriterion.class);
        assertThat(agglomerate.getReturnType()).isEqualTo(LinkageMatrix.class);

        for (Method method : LanceWilliamsEngine.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertThat(parameterType)
                        .as("no public LanceWilliamsEngine method may accept a raw matrix array")
                        .isNotIn(double[][].class, double[].class);
            }
        }
    }

    private static void assertRow(
            LinkageMatrix matrix, int rowIndex, int expectedIdx1, int expectedIdx2,
            double expectedDistance, int expectedSize) {
        List<LinkageStep> rows = matrix.rows();
        LinkageStep row = rows.get(rowIndex);
        assertThat(row.idx1()).isEqualTo(expectedIdx1);
        assertThat(row.idx2()).isEqualTo(expectedIdx2);
        assertThat(row.mergeDistance()).isCloseTo(expectedDistance, within(TOLERANCE));
        assertThat(row.size()).isEqualTo(expectedSize);
    }
}
