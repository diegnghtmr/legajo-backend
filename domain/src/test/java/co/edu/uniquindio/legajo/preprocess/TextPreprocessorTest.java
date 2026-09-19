package co.edu.uniquindio.legajo.preprocess;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §6.2: exactly five steps (NFC, lowercase, tokenize, stopword removal, optional
 * Porter stemming), no token-length filter, deterministic output.
 */
class TextPreprocessorTest {

    private final TextPreprocessor preprocessor = new TextPreprocessor();

    @Test
    void lowercasesWithLocaleRoot() {
        PreprocessedText result = preprocessor.preprocess("HELLO World");

        assertThat(result.tokens()).containsExactly("hello", "world");
    }

    @Test
    void normalizesToNfcBeforeTokenizing() {
        // "café" written with a combining acute accent (NFD form): e + U+0301.
        String nfdInput = "caf" + "é";
        assertThat(Normalizer.isNormalized(nfdInput, Normalizer.Form.NFC)).isFalse();

        PreprocessedText result = preprocessor.preprocess(nfdInput);

        assertThat(result.tokens()).containsExactly("café");
    }

    @Test
    void tokenizesOnLettersAndDigitsOnly() {
        PreprocessedText result = preprocessor.preprocess("AI, ML. co-operative 3D-model!");

        assertThat(result.tokens()).containsExactly("ai", "ml", "co", "operative", "3d", "model");
    }

    @Test
    void removesEnglishStopwordsFromVersionedResource() {
        PreprocessedText result = preprocessor.preprocess("The cat sat on the mat");

        assertThat(result.tokens()).containsExactly("cat", "sat", "mat");
    }

    @Test
    void keepsShortDomainRelevantTokensWithNoLengthFilter() {
        PreprocessedText result = preprocessor.preprocess("AI and ML are great fields");

        assertThat(result.tokens()).contains("ai", "ml").doesNotContain("and", "are");
    }

    @Test
    void stemmingIsDisabledByDefault() {
        PreprocessedText result = preprocessor.preprocess("running horses");

        assertThat(result.stemmingApplied()).isFalse();
        assertThat(result.tokens()).containsExactly("running", "horses");
    }

    @Test
    void appliesPorterStemmingWhenExplicitlyEnabled() {
        PreprocessedText result = preprocessor.preprocess("running horses", true);

        assertThat(result.stemmingApplied()).isTrue();
        assertThat(result.tokens()).containsExactly("run", "hors");
    }

    @Test
    void isDeterministicForTheSameInput() {
        PreprocessedText first = preprocessor.preprocess("Determinism Matters!");
        PreprocessedText second = preprocessor.preprocess("Determinism Matters!");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void tokenListIsImmutable() {
        PreprocessedText result = preprocessor.preprocess("immutable tokens");

        assertThat(result.tokens()).isInstanceOf(List.class);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.tokens().add("extra"));
    }
}
