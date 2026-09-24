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
 * jqwik properties for the {@code jaccard} capability: symmetry, identity
 * (a token stream compared with itself scores 1.0), and the coefficient staying within the
 * closed [0,1] range for every pair.
 */
class JaccardPropertyTest {

    private static final double TOLERANCE = 1e-9;
    private final Jaccard jaccard = new Jaccard();

    @Property
    void computeIsSymmetric(@ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult ab = jaccard.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);
        SimilarityResult ba = jaccard.compute(input(tokensB), input(tokensA), SimilarityContext.EMPTY);

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
        assertThat(ab.rawValue()).isEqualTo(ba.rawValue());
    }

    @Property
    void identicalTokenStreamScoresOne(@ForAll("tokenLists") List<String> tokens) {
        SimilarityResult result = jaccard.compute(input(tokens), input(tokens), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRange(
            @ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult result = jaccard.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    @Provide
    Arbitrary<List<String>> tokenLists() {
        return Arbitraries.of("the", "cat", "sat", "on", "mat", "dog").list().ofMinSize(0).ofMaxSize(8);
    }

    private static SimilarityInput input(List<String> tokens) {
        return new SimilarityInput(String.join(" ", tokens), tokens);
    }
}
