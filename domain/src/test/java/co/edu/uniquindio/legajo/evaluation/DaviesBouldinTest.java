package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * Davies-Bouldin in V space at a fixed k (TRD §6.5, "Definición de Davies-Bouldin"),
 * including the {@code M_ij = 0 -> null} rule.
 */
class DaviesBouldinTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void matchesTheHandComputedGoldenValueForTwoClearClusters() {
        // v0=(1,0), v1=(0,1) -> cluster0; v2=(-1,0), v3=(0,-1) -> cluster1.
        // centroid0 = (0.5, 0.5), centroid1 = (-0.5, -0.5).
        // sigma0 = mean(dist(v0,centroid0), dist(v1,centroid0)) = sqrt(0.5) for both members.
        // sigma1 = sqrt(0.5) by the same symmetric computation.
        // M01 = dist(centroid0, centroid1) = dist((0.5,0.5),(-0.5,-0.5)) = sqrt(2).
        // term(0) = (sigma0+sigma1)/M01 = (2*sqrt(0.5))/sqrt(2) = sqrt(2)/sqrt(2) = 1.
        // term(1) is identical by symmetry, so DB = (1/2)*(1+1) = 1.0 exactly.
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(-1.0, 0.0),
                List.of(0.0, -1.0));
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1, 1), 2);

        OptionalDouble db = DaviesBouldin.of(assignment, vectors);

        assertThat(db).isPresent();
        assertThat(db.getAsDouble()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void singletonSigmaIsZeroButTheOverallResultCanStillBeFinite() {
        // v0=(1,0) is a singleton cluster0; v1=(0,1), v2=(-1,0) form cluster1.
        // sigma0 = 0 (a singleton's only member sits exactly at its own centroid).
        // centroid1 = (-0.5, 0.5). sigma1 = mean(dist(v1,centroid1), dist(v2,centroid1))
        //           = mean(sqrt(0.5), sqrt(0.5)) = sqrt(0.5) = 1/sqrt(2).
        // M01 = dist((1,0), (-0.5,0.5)) = sqrt(1.5^2 + 0.5^2) = sqrt(2.5).
        // term(0) = term(1) = (0 + 1/sqrt(2)) / sqrt(2.5) = 1/sqrt(5) (both clusters have
        // only one "other" cluster), so DB = (1/2)*(2/sqrt(5)) = 1/sqrt(5) = 0.4472135954999579.
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(-1.0, 0.0));
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1, 1), 2);

        OptionalDouble db = DaviesBouldin.of(assignment, vectors);

        assertThat(db).isPresent();
        assertThat(db.getAsDouble()).isCloseTo(1.0 / Math.sqrt(5.0), within(TOLERANCE));
    }

    @Test
    void reportsNullWhenTwoCentroidsCoincide() {
        // cluster0={v0,v1}=(1,0),(-1,0) -> centroid0=(0,0). cluster1={v2,v3}=(0,1),(0,-1)
        // -> centroid1=(0,0). M01=0 exactly, so both clusters' quotient is +infinity (TRD
        // §6.5: "el cociente se toma como +infinito") and DB is undefined for this k.
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(-1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(0.0, -1.0));
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1, 1), 2);

        OptionalDouble db = DaviesBouldin.of(assignment, vectors);

        assertThat(db).isEmpty();
    }

    @Test
    void reportsNullWhenAZeroNumeratorAlsoCoincidesWithAZeroMij() {
        // cluster0={v0=(1,0)} and cluster1={v1=(1,0)} are singletons sharing the exact same
        // vector: sigma0=sigma1=0 and M01=0, so term01 is (0+0)/0. Plain IEEE-754 division
        // resolves that to NaN, not +infinity, and a NaN never compares greater than anything
        // (including the loop's running max), so an implementation without an explicit M_ij=0
        // guard would silently drop this term instead of forcing the cluster's max to
        // +infinity. cluster2={v2=(0,1), v3=(0,-1)} (centroid (0,0), sigma2=1) supplies a
        // finite, larger-than-covered alternative term (1.0) for clusters 0 and 1, so a
        // buggy implementation that drops the NaN reports a finite DB (1.0) instead of the
        // undefined result the TRD requires: with the guard, term01=term10=+infinity makes
        // clusters 0 and 1's max +infinity, the overall sum +infinity, and DB undefined.
        List<List<Double>> vectors = List.of(
                List.of(1.0, 0.0),
                List.of(1.0, 0.0),
                List.of(0.0, 1.0),
                List.of(0.0, -1.0));
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1, 2, 2), 3);

        OptionalDouble db = DaviesBouldin.of(assignment, vectors);

        assertThat(db).isEmpty();
    }

    @Test
    void rejectsANullAssignment() {
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));

        assertThatNullPointerException().isThrownBy(() -> DaviesBouldin.of(null, vectors));
    }

    @Test
    void rejectsANullVectorList() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1), 2);

        assertThatNullPointerException().isThrownBy(() -> DaviesBouldin.of(assignment, null));
    }

    @Test
    void rejectsAVectorListWhoseSizeDoesNotMatchTheAssignment() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 0, 1), 2);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));

        assertThatIllegalArgumentException().isThrownBy(() -> DaviesBouldin.of(assignment, vectors));
    }

    @Test
    void rejectsAnAssignmentWithFewerThanTwoClusters() {
        ClusterAssignment singleCluster = new ClusterAssignment(List.of(0, 0), 1);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(0.0, 1.0));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DaviesBouldin.of(singleCluster, vectors))
                .withMessageContaining("at least 2 clusters");
    }

    @Test
    void rejectsMismatchedDimensions() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1), 2);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(1.0, 0.0, 0.0));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DaviesBouldin.of(assignment, vectors))
                .withMessageContaining("dimension");
    }

    @Test
    void rejectsANonUnitVector() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1), 2);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(2.0, 0.0));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DaviesBouldin.of(assignment, vectors))
                .withMessageContaining("L2-normalized");
    }

    @Test
    void rejectsAnEmptyInnerVector() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1), 2);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DaviesBouldin.of(assignment, vectors))
                .withMessageContaining("must not be empty");
    }

    @Test
    void rejectsANonFiniteComponent() {
        ClusterAssignment assignment = new ClusterAssignment(List.of(0, 1), 2);
        List<List<Double>> vectors = List.of(List.of(1.0, 0.0), List.of(Double.NaN, 0.0));

        assertThatIllegalArgumentException().isThrownBy(() -> DaviesBouldin.of(assignment, vectors));
    }
}
