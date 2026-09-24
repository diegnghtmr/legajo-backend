package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * One linkage criterion's evaluation summary: the cophenetic correlation (primary,
 * against the whole tree) plus the two partition-quality metrics at {@code k_ref}. This is
 * the per-linkage row {@link ClusteringRanking} consumes.
 */
class LinkageEvaluationTest {

    @Test
    void acceptsAValidEvaluationWithADefinedDaviesBouldin() {
        LinkageEvaluation evaluation = new LinkageEvaluation(
                new SingleLinkage(), 0.9, 0.6, OptionalDouble.of(0.4));

        assertThat(evaluation.criterion()).isInstanceOf(SingleLinkage.class);
        assertThat(evaluation.copheneticCorrelation()).isEqualTo(0.9);
        assertThat(evaluation.meanSilhouetteAtKRef()).isEqualTo(0.6);
        assertThat(evaluation.daviesBouldinAtKRef()).isEqualTo(OptionalDouble.of(0.4));
    }

    @Test
    void acceptsAnUndefinedDaviesBouldin() {
        LinkageEvaluation evaluation = new LinkageEvaluation(
                new SingleLinkage(), 0.9, 0.6, OptionalDouble.empty());

        assertThat(evaluation.daviesBouldinAtKRef()).isEmpty();
    }

    @Test
    void rejectsANullCriterion() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LinkageEvaluation(null, 0.9, 0.6, OptionalDouble.empty()));
    }

    @Test
    void rejectsANullDaviesBouldinOptional() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LinkageEvaluation(new SingleLinkage(), 0.9, 0.6, null));
    }

    @Test
    void rejectsANonFiniteCopheneticCorrelation() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(new SingleLinkage(), Double.NaN, 0.6, OptionalDouble.empty()));
    }

    @Test
    void rejectsACopheneticCorrelationOutsideMinusOneToOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(new SingleLinkage(), 1.5, 0.6, OptionalDouble.empty()));
    }

    @Test
    void rejectsANonFiniteMeanSilhouette() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(
                        new SingleLinkage(), 0.9, Double.POSITIVE_INFINITY, OptionalDouble.empty()));
    }

    @Test
    void rejectsAMeanSilhouetteOutsideMinusOneToOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(new SingleLinkage(), 0.9, 2.0, OptionalDouble.empty()));
    }

    @Test
    void rejectsANonFiniteDefinedDaviesBouldin() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(
                        new SingleLinkage(), 0.9, 0.6, OptionalDouble.of(Double.NaN)));
    }

    @Test
    void rejectsANegativeDefinedDaviesBouldin() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageEvaluation(
                        new SingleLinkage(), 0.9, 0.6, OptionalDouble.of(-0.1)));
    }
}
