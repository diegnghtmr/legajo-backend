package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * Mean silhouette against D at a fixed k,
 * including the singleton s(i) = 0 convention (Rousseeuw).
 *
 * <p><b>Shared fixture, hand-derived independently of the implementation.</b> Four 2D unit
 * vectors at 0°, 60°, 180°, 240°: v0=(1,0), v1=(0.5, sqrt(3)/2), v2=(-1,0),
 * v3=(-0.5, -sqrt(3)/2). Dot products touching v0 or v2 are exact (their y-component is 0):
 * cos(v0,v1)=0.5, cos(v0,v2)=-1, cos(v0,v3)=-0.5, cos(v1,v2)=-0.5, cos(v2,v3)=0.5; only
 * cos(v1,v3)=-1 relies on floating-point sqrt(3)/2, and lands within 1e-15 of exactly -1.
 * D = 1 - cos gives:
 *
 * <pre>
 *        0     1     2     3
 * 0  [   0,  0.5,    2,  1.5 ]
 * 1  [ 0.5,    0,  1.5,    2 ]
 * 2  [   2,  1.5,    0,  0.5 ]
 * 3  [ 1.5,    2,  0.5,    0 ]
 * </pre>
 */
class MeanSilhouetteTest {

    private static final double TOLERANCE = 1e-9;
    private static final double SQRT_3_OVER_2 = Math.sqrt(3.0) / 2.0;

    private static final List<List<Double>> FOUR_POINT_FIXTURE = List.of(
            List.of(1.0, 0.0),
            List.of(0.5, SQRT_3_OVER_2),
            List.of(-1.0, 0.0),
            List.of(-0.5, -SQRT_3_OVER_2));

    @Test
    void matchesTheHandComputedGoldenValueForTwoClearClusters() {
        // Cluster {0,1} vs {2,3}. Every point: a(i) = 0.5, b(i) = mean(2, 1.5) = 1.75 (or the
        // symmetric case), s(i) = (1.75 - 0.5) / 1.75 = 1.25 / 1.75 = 5/7 for all four points
        // by the fixture's symmetry, so the mean is 5/7 exactly.
        DistanceMatrix d = DistanceMatrix.cosineDistance(FOUR_POINT_FIXTURE);
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1, 1), 2);

        double result = MeanSilhouette.of(assignment, d);

        assertThat(result).isCloseTo(5.0 / 7.0, within(TOLERANCE));
    }

    @Test
    void appliesTheSingletonConventionAlongsideNegativeSilhouettes() {
        // k=3: cluster0={0} singleton -> s(0)=0; cluster1={1,2}; cluster2={3} singleton ->
        // s(3)=0. For i=1: a(1)=D(1,2)=1.5, other clusters {0}:D(1,0)=0.5, {3}:D(1,3)=2,
        // b(1)=min(0.5,2)=0.5, s(1)=(0.5-1.5)/1.5=-2/3. By symmetry i=2 also gives -2/3.
        // Mean = (0 + (-2/3) + (-2/3) + 0) / 4 = -1/3.
        DistanceMatrix d = DistanceMatrix.cosineDistance(FOUR_POINT_FIXTURE);
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1, 1, 2), 3);

        double result = MeanSilhouette.of(assignment, d);

        assertThat(result).isCloseTo(-1.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void treatsAZeroDenominatorAsSilhouetteZeroRatherThanNaN() {
        // v0 and v1 are bit-identical, so D(0,1)=0; v2 is also identical to v0, so D(0,2)=0
        // too. For i=0: a(0)=D(0,1)=0, b(0)=D(0,2)=0, max(a,b)=0 -> the (b-a)/max(a,b) formula
        // is 0/0 and must resolve to s(i)=0 by convention (scikit-learn's silhouette_samples
        // uses the same rule for this edge case), not NaN.
        List<List<Double>> identicalTriple = List.of(
                List.of(1.0, 0.0),
                List.of(1.0, 0.0),
                List.of(1.0, 0.0));
        DistanceMatrix d = DistanceMatrix.cosineDistance(identicalTriple);
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1), 2);

        double result = MeanSilhouette.of(assignment, d);

        assertThat(result).isCloseTo(0.0, within(TOLERANCE));
        assertThat(Double.isNaN(result)).isFalse();
    }

    @Test
    void rejectsANullAssignment() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(FOUR_POINT_FIXTURE);

        assertThatNullPointerException().isThrownBy(() -> MeanSilhouette.of(null, d));
    }

    @Test
    void rejectsANullDistanceMatrix() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1, 1), 2);

        assertThatNullPointerException().isThrownBy(() -> MeanSilhouette.of(assignment, null));
    }

    @Test
    void rejectsADistanceMatrixWhoseSizeDoesNotMatchTheAssignment() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(FOUR_POINT_FIXTURE);
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1), 2);

        assertThatIllegalArgumentException().isThrownBy(() -> MeanSilhouette.of(assignment, d));
    }

    @Test
    void rejectsAnAssignmentWithFewerThanTwoClusters() {
        DistanceMatrix d = DistanceMatrix.cosineDistance(FOUR_POINT_FIXTURE);
        ClusterAssignment singleCluster = new ClusterAssignment(List.of(0, 0, 0, 0), 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MeanSilhouette.of(singleCluster, d))
                .withMessageContaining("at least 2 clusters");
    }
}
