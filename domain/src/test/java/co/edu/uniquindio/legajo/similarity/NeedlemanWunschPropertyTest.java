package co.edu.uniquindio.legajo.similarity;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * jqwik properties for the {@code needleman-wunsch} capability (TRD §6.3, §13): symmetry,
 * identity (a sequence compared with itself scores 1.0), the normalized score staying
 * within the closed [0,1] range, and the TRD's stated upper bound of {@code m/M} for
 * sequences of different lengths (m = min(lenA, lenB), M = max(lenA, lenB)).
 */
class NeedlemanWunschPropertyTest {

    private static final double TOLERANCE = 1e-9;
    private final NeedlemanWunsch needlemanWunsch = new NeedlemanWunsch();

    @Property
    void computeIsSymmetric(@ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult ab = needlemanWunsch.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);
        SimilarityResult ba = needlemanWunsch.compute(input(tokensB), input(tokensA), SimilarityContext.EMPTY);

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
        assertThat(ab.rawValue()).isEqualTo(ba.rawValue());
    }

    @Property
    void identicalSequenceScoresOne(@ForAll("tokenLists") List<String> tokens) {
        SimilarityResult result = needlemanWunsch.compute(input(tokens), input(tokens), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRange(
            @ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult result = needlemanWunsch.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    @Property
    void normalizedScoreIsUpperBoundedByTheShorterOverLongerLengthRatio(
            @ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        int lenA = tokensA.size();
        int lenB = tokensB.size();
        if (lenA == 0 && lenB == 0) {
            return; // 0/0 is defined separately as 1.0, not covered by the m/M bound.
        }
        double m = Math.min(lenA, lenB);
        double bigM = Math.max(lenA, lenB);

        SimilarityResult result = needlemanWunsch.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isLessThanOrEqualTo(m / bigM + TOLERANCE);
    }

    @Provide
    Arbitrary<List<String>> tokenLists() {
        return Arbitraries.of("the", "cat", "sat", "on", "mat", "dog").list().ofMinSize(0).ofMaxSize(8);
    }

    private static SimilarityInput input(List<String> tokens) {
        return new SimilarityInput(String.join(" ", tokens), tokens);
    }
}
