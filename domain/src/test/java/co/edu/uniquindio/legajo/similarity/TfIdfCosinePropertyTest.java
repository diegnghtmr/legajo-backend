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
 * jqwik properties for the {@code tfidf-cosine} capability: symmetry, identity
 * (a token stream compared with itself scores 1.0), and the normalized score staying within
 * the closed [0,1] range. Each generated pair is treated as its own two-document corpus
 * (df/N computed over exactly {@code {tokensA, tokensB}}) — a property test has no larger
 * reference corpus to draw from, and df/N are order-independent (they count document
 * membership, not position), so this stays consistent with the fixed "over the whole
 * corpus, never the pair" rule for whatever corpus is actually passed in.
 */
class TfIdfCosinePropertyTest {

    private static final double TOLERANCE = 1e-9;
    private final TfIdfCosine tfIdfCosine = new TfIdfCosine();

    @Property
    void computeIsSymmetric(@ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult ab = tfIdfCosine.compute(input(tokensA), input(tokensB), context(tokensA, tokensB));
        SimilarityResult ba = tfIdfCosine.compute(input(tokensB), input(tokensA), context(tokensB, tokensA));

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
    }

    @Property
    void identicalTokenStreamScoresOne(@ForAll("tokenLists") List<String> tokens) {
        SimilarityResult result = tfIdfCosine.compute(input(tokens), input(tokens), context(tokens, tokens));

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRange(
            @ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult result = tfIdfCosine.compute(input(tokensA), input(tokensB), context(tokensA, tokensB));

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    @Provide
    Arbitrary<List<String>> tokenLists() {
        return Arbitraries.of("the", "cat", "sat", "on", "mat", "dog").list().ofMinSize(0).ofMaxSize(8);
    }

    private static SimilarityInput input(List<String> tokens) {
        return new SimilarityInput(String.join(" ", tokens), tokens);
    }

    private static SimilarityContext context(List<String> tokensA, List<String> tokensB) {
        return SimilarityContext.withTfIdfIndex(TfIdfCorpusIndex.from(List.of(tokensA, tokensB)));
    }
}
