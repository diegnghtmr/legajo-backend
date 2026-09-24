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
 * The ranking rule: the highest cophenetic correlation
 * wins by default; the tie set is every linkage within 1e-3 of the highest; the tie set is
 * resolved by highest mean silhouette at {@code k_ref}, then lowest Davies-Bouldin at
 * {@code k_ref}, then declaration order (single, complete, average, ward); if the silhouette
 * leader at {@code k_ref} differs from the resolved cophenetic winner, both are reported.
 */
class ClusteringRankingTest {

    @Test
    void picksTheSoleCopheneticLeaderWhenThereIsNoTie() {
        // No tie: single is alone at the top by more than 1e-3, so it wins outright without
        // needing any tie-break step. complete has the highest silhouette overall, so the
        // two leaders differ and both must be surfaced.
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.95, 0.2, OptionalDouble.of(0.5));
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.50, 0.9, OptionalDouble.of(0.1));
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.40, 0.3, OptionalDouble.of(0.2));
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.30, 0.1, OptionalDouble.of(0.6));

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.copheneticTieSet()).containsExactly(single);
        assertThat(result.bestTreeFidelity()).isEqualTo(single);
        assertThat(result.bestPartitionAtKRef()).isEqualTo(complete);
        assertThat(result.leadersDiffer()).isTrue();
        assertThat(result.sampleSize()).isEqualTo(20);
    }

    @Test
    void constructsAThreeWayTieAndResolvesItByMeanSilhouette() {
        // maxCorrelation = 0.9010 (complete). |0.9010 - x| <= 1e-3: single (diff 5e-4) and
        // ward (diff 2e-4) both qualify; average (diff 0.101) does not. Tie set =
        // {single, complete, ward}. complete has the highest silhouette (0.7) within that
        // set, and also the highest overall, so the two leaders agree here.
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9005, 0.5, OptionalDouble.of(0.3));
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.9010, 0.7, OptionalDouble.of(0.25));
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.8000, 0.4, OptionalDouble.of(0.2));
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.9008, 0.6, OptionalDouble.of(0.28));

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.copheneticTieSet()).containsExactlyInAnyOrder(single, complete, ward);
        assertThat(result.bestTreeFidelity()).isEqualTo(complete);
        assertThat(result.bestPartitionAtKRef()).isEqualTo(complete);
        assertThat(result.leadersDiffer()).isFalse();
    }

    @Test
    void resolvesATiedSilhouetteByLowestDaviesBouldin() {
        // single and complete tie exactly on correlation (0.9000) and on silhouette (0.6);
        // average and ward sit far below the 1e-3 threshold. complete's lower DB (0.3 vs 0.5)
        // breaks the tie.
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9000, 0.6, OptionalDouble.of(0.5));
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.9000, 0.6, OptionalDouble.of(0.3));
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.5000, 0.2, OptionalDouble.of(0.4));
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.4000, 0.1, OptionalDouble.of(0.6));

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.copheneticTieSet()).containsExactlyInAnyOrder(single, complete);
        assertThat(result.bestTreeFidelity()).isEqualTo(complete);
    }

    @Test
    void anUndefinedDaviesBouldinLosesTheTieBreakToAnyDefinedValue() {
        // Author decision (not otherwise specified): an
        // undefined ("null") Davies-Bouldin is treated as worse than any finite value for
        // this tie-break step, since it carries no evidence of a well-separated partition.
        // single and complete are still tied on correlation and silhouette; single's DB is
        // undefined, so complete (a defined, finite DB) wins.
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9000, 0.6, OptionalDouble.empty());
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.9000, 0.6, OptionalDouble.of(0.3));
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.5000, 0.2, OptionalDouble.of(0.4));
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.4000, 0.1, OptionalDouble.of(0.6));

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.bestTreeFidelity()).isEqualTo(complete);
    }

    @Test
    void resolvesAFinalTieByDeclarationOrder() {
        // single, complete and ward are all tied on correlation, silhouette, and DB (all
        // undefined); average sits far below the correlation threshold. Declaration order
        // (single, complete, average, ward) makes single win.
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9, 0.6, OptionalDouble.empty());
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.9, 0.6, OptionalDouble.empty());
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.5, 0.2, OptionalDouble.empty());
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.9, 0.6, OptionalDouble.empty());

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.copheneticTieSet()).containsExactlyInAnyOrder(single, complete, ward);
        assertThat(result.bestTreeFidelity()).isEqualTo(single);
    }

    @Test
    void resolvesTheSilhouetteLeaderTieByDeclarationOrderToo() {
        // complete and average tie for the highest silhouette (0.9) across ALL FOUR
        // linkages (not just the cophenetic tie set); declaration order (complete before
        // average) is the same deterministic fallback used for the cophenetic tie-break
        // (author decision, not otherwise specified for this specific leader).
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9, 0.5, OptionalDouble.empty());
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.5, 0.9, OptionalDouble.empty());
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.4, 0.9, OptionalDouble.empty());
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.3, 0.1, OptionalDouble.empty());

        ClusteringRankingResult result = ClusteringRanking.of(List.of(single, complete, average, ward), 20);

        assertThat(result.bestPartitionAtKRef()).isEqualTo(complete);
    }

    @Test
    void rejectsANullEvaluationsList() {
        assertThatNullPointerException().isThrownBy(() -> ClusteringRanking.of(null, 20));
    }

    @Test
    void rejectsAnEvaluationsListNotEqualToFour() {
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9, 0.6, OptionalDouble.empty());
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.8, 0.5, OptionalDouble.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClusteringRanking.of(List.of(single, complete), 20))
                .withMessageContaining("four");
    }

    @Test
    void rejectsDuplicateCriteriaInTheEvaluationsList() {
        LinkageEvaluation singleA = new LinkageEvaluation(new SingleLinkage(), 0.9, 0.6, OptionalDouble.empty());
        LinkageEvaluation singleB = new LinkageEvaluation(new SingleLinkage(), 0.8, 0.5, OptionalDouble.empty());
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.7, 0.4, OptionalDouble.empty());
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.6, 0.3, OptionalDouble.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClusteringRanking.of(List.of(singleA, singleB, average, ward), 20))
                .withMessageContaining("duplicate");
    }

    @Test
    void rejectsASampleSizeBelowOne() {
        LinkageEvaluation single = new LinkageEvaluation(new SingleLinkage(), 0.9, 0.6, OptionalDouble.empty());
        LinkageEvaluation complete = new LinkageEvaluation(new CompleteLinkage(), 0.8, 0.5, OptionalDouble.empty());
        LinkageEvaluation average = new LinkageEvaluation(new AverageLinkage(), 0.7, 0.4, OptionalDouble.empty());
        LinkageEvaluation ward = new LinkageEvaluation(new WardLinkage(), 0.6, 0.3, OptionalDouble.empty());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClusteringRanking.of(List.of(single, complete, average, ward), 0));
    }
}
