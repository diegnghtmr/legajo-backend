package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.AverageLinkage;
import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.clustering.WardLinkage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The full ranking rule output (TRD §6.5): every linkage's evaluation, the cophenetic tie
 * set, the two possibly-differing leaders, and the sample-size context. Re-validates its own
 * cross-field invariants rather than trusting {@link ClusteringRanking}, the house rule this
 * package's other validated value types already follow.
 */
class ClusteringRankingResultTest {

    private static final LinkageEvaluation SINGLE =
            new LinkageEvaluation(new SingleLinkage(), 0.9, 0.5, OptionalDouble.of(0.3));
    private static final LinkageEvaluation COMPLETE =
            new LinkageEvaluation(new CompleteLinkage(), 0.85, 0.6, OptionalDouble.of(0.2));
    private static final LinkageEvaluation AVERAGE =
            new LinkageEvaluation(new AverageLinkage(), 0.95, 0.4, OptionalDouble.of(0.4));
    private static final LinkageEvaluation WARD =
            new LinkageEvaluation(new WardLinkage(), 0.7, 0.3, OptionalDouble.empty());

    private static final List<LinkageEvaluation> ALL_FOUR = List.of(SINGLE, COMPLETE, AVERAGE, WARD);

    @Test
    void acceptsAConsistentResultWhereTheLeadersDiffer() {
        ClusteringRankingResult result = new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, COMPLETE, true, 20);

        assertThat(result.evaluations()).containsExactlyElementsOf(ALL_FOUR);
        assertThat(result.copheneticTieSet()).containsExactly(AVERAGE);
        assertThat(result.bestTreeFidelity()).isEqualTo(AVERAGE);
        assertThat(result.bestPartitionAtKRef()).isEqualTo(COMPLETE);
        assertThat(result.leadersDiffer()).isTrue();
        assertThat(result.sampleSize()).isEqualTo(20);
    }

    @Test
    void acceptsAConsistentResultWhereTheLeadersAgree() {
        ClusteringRankingResult result = new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, AVERAGE, false, 20);

        assertThat(result.leadersDiffer()).isFalse();
    }

    @Test
    void rejectsALeadersDifferFlagInconsistentWithTheActualLeaders() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, COMPLETE, false, 20));
    }

    @Test
    void rejectsATieSetThatIsNotASubsetOfEvaluations() {
        LinkageEvaluation outsider = new LinkageEvaluation(new SingleLinkage(), 0.99, 0.1, OptionalDouble.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteringRankingResult(
                List.of(COMPLETE, AVERAGE, WARD, SINGLE), List.of(outsider), outsider, COMPLETE, true, 20));
    }

    @Test
    void rejectsABestTreeFidelityNotInTheTieSet() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), COMPLETE, COMPLETE, false, 20));
    }

    @Test
    void rejectsABestPartitionNotInEvaluations() {
        LinkageEvaluation outsider = new LinkageEvaluation(new SingleLinkage(), 0.99, 0.1, OptionalDouble.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, outsider, true, 20));
    }

    @Test
    void rejectsAnEmptyEvaluationsList() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusteringRankingResult(List.of(), List.of(AVERAGE), AVERAGE, AVERAGE, false, 20));
    }

    @Test
    void rejectsAnEmptyTieSet() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusteringRankingResult(ALL_FOUR, List.of(), AVERAGE, AVERAGE, false, 20));
    }

    @Test
    void rejectsASampleSizeBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, AVERAGE, false, 0));
    }

    @Test
    void rejectsNullComponents() {
        assertThatNullPointerException().isThrownBy(() -> new ClusteringRankingResult(
                null, List.of(AVERAGE), AVERAGE, AVERAGE, false, 20));
        assertThatNullPointerException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, null, AVERAGE, AVERAGE, false, 20));
        assertThatNullPointerException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), null, AVERAGE, false, 20));
        assertThatNullPointerException().isThrownBy(() -> new ClusteringRankingResult(
                ALL_FOUR, List.of(AVERAGE), AVERAGE, null, false, 20));
    }
}
