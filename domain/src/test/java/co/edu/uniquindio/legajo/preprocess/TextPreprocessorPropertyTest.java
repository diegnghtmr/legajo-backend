package co.edu.uniquindio.legajo.preprocess;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jqwik properties for the two behaviors that are naturally property-based:
 * idempotence of the NFC + lowercase step, and
 * "no stopword survives preprocessing".
 */
class TextPreprocessorPropertyTest {

    private static final Set<String> STOPWORDS = Set.of("the", "a", "an", "and", "of", "in");
    private final TextPreprocessor preprocessor = new TextPreprocessor(STOPWORDS);

    @Property
    void normalizeAndLowercaseIsIdempotent(@ForAll @AlphaChars @StringLength(min = 1, max = 40) String text) {
        String once = TextPreprocessor.normalizeAndLowercase(text);
        String twice = TextPreprocessor.normalizeAndLowercase(once);

        assertThat(twice).isEqualTo(once);
    }

    @Property
    void noStopwordSurvivesInOutput(@ForAll("wordsFromVocabulary") List<String> words) {
        String text = String.join(" ", words);

        PreprocessedText result = preprocessor.preprocess(text);

        assertThat(result.tokens()).noneMatch(STOPWORDS::contains);
    }

    @net.jqwik.api.Provide
    net.jqwik.api.Arbitrary<List<String>> wordsFromVocabulary() {
        return net.jqwik.api.Arbitraries.of(
                        "the", "a", "an", "and", "of", "in", "cat", "dog", "corpus", "ai", "ml", "algorithm")
                .list()
                .ofMinSize(0)
                .ofMaxSize(20);
    }
}
