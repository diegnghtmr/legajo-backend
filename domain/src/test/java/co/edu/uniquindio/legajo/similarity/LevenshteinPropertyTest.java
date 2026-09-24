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
 * jqwik properties for the {@code levenshtein} capability: symmetry, identity
 * (a sequence compared with itself scores 1.0), and the normalized score staying within
 * the closed [0,1] range for arbitrary token streams.
 */
class LevenshteinPropertyTest {

    private static final double TOLERANCE = 1e-9;
    private final Levenshtein levenshtein = new Levenshtein();

    @Property
    void computeIsSymmetric(@ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult ab = levenshtein.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);
        SimilarityResult ba = levenshtein.compute(input(tokensB), input(tokensA), SimilarityContext.EMPTY);

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
        assertThat(ab.rawValue()).isEqualTo(ba.rawValue());
    }

    @Property
    void identicalSequenceScoresOne(@ForAll("tokenLists") List<String> tokens) {
        SimilarityResult result = levenshtein.compute(input(tokens), input(tokens), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRange(
            @ForAll("tokenLists") List<String> tokensA, @ForAll("tokenLists") List<String> tokensB) {
        SimilarityResult result = levenshtein.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    // The properties above only ever exercised ASCII tokens (fixed English words); tokens
    // are compared with plain Object.equals() (see LevenshteinCore.matches), which has no
    // ASCII-specific logic, so these three properties should hold identically over
    // non-ASCII tokens (accents, CJK, emoji). These re-run the same properties over a
    // dedicated non-ASCII arbitrary to prove that assumption instead of leaving it implicit.

    @Property
    void computeIsSymmetricOverNonAsciiTokens(
            @ForAll("nonAsciiTokenLists") List<String> tokensA, @ForAll("nonAsciiTokenLists") List<String> tokensB) {
        SimilarityResult ab = levenshtein.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);
        SimilarityResult ba = levenshtein.compute(input(tokensB), input(tokensA), SimilarityContext.EMPTY);

        assertThat(ab.normalizedScore()).isCloseTo(ba.normalizedScore(), within(TOLERANCE));
        assertThat(ab.rawValue()).isEqualTo(ba.rawValue());
    }

    @Property
    void identicalSequenceScoresOneOverNonAsciiTokens(@ForAll("nonAsciiTokenLists") List<String> tokens) {
        SimilarityResult result = levenshtein.compute(input(tokens), input(tokens), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Property
    void normalizedScoreStaysInZeroToOneRangeOverNonAsciiTokens(
            @ForAll("nonAsciiTokenLists") List<String> tokensA, @ForAll("nonAsciiTokenLists") List<String> tokensB) {
        SimilarityResult result = levenshtein.compute(input(tokensA), input(tokensB), SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isBetween(0.0 - TOLERANCE, 1.0 + TOLERANCE);
    }

    @Provide
    Arbitrary<List<String>> tokenLists() {
        return Arbitraries.of("the", "cat", "sat", "on", "mat", "dog").list().ofMinSize(0).ofMaxSize(8);
    }

    @Provide
    Arbitrary<List<String>> nonAsciiTokenLists() {
        return Arbitraries.of("café", "naïve", "日本語", "école", "emoji😀", "grüße")
                .list().ofMinSize(0).ofMaxSize(8);
    }

    private static SimilarityInput input(List<String> tokens) {
        return new SimilarityInput(String.join(" ", tokens), tokens);
    }
}
