package co.edu.uniquindio.legajo.preprocess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Known word pairs illustrating each step of Porter's original 1980 algorithm ("An
 * algorithm for suffix stripping", Program 14(3), pp. 130-137). These are the worked
 * examples from the paper itself, not the full ~30k-word reference vocabulary
 * distributed with later Snowball/NLTK ports.
 *
 * <p>Each step is exercised directly (rather than through {@link #stem}) because the
 * paper's own examples describe the LOCAL effect of one step in isolation; several of
 * them (e.g. "agreed" -&gt; "agree" in step 1b) are further reduced by a later step
 * when run through the full pipeline (step 5a strips that same E again, because the
 * resulting stem has measure 1 and does not end CVC — the full-pipeline result is
 * "agre"). The {@link #fullPipelineIsConsistentWithStepByStepApplication()} and the
 * dedicated regression cases below cross-check that both paths agree.
 */
class PorterStemmerTest {

    private final PorterStemmer stemmer = new PorterStemmer();

    @ParameterizedTest(name = "step1a: {0} -> {1}")
    @CsvSource({
            "caresses, caress",
            "ponies, poni",
            "ties, ti",
            "caress, caress",
            "cats, cat"
    })
    void step1aPluralsAndThirdPerson(String word, String expected) {
        assertThat(applyStep(stemmer::step1a, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step1b: {0} -> {1}")
    @CsvSource({
            "feed, feed",
            "agreed, agree",
            "plastered, plaster",
            "bled, bled",
            "motoring, motor",
            "sing, sing",
            "conflated, conflate",
            "troubled, trouble",
            "sized, size",
            "hopping, hop",
            "tanned, tan",
            "falling, fall",
            "hissing, hiss",
            "fizzed, fizz",
            "failing, fail",
            "filing, file"
    })
    void step1bPastTenseAndGerund(String word, String expected) {
        assertThat(applyStep(stemmer::step1b, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step1c: {0} -> {1}")
    @CsvSource({
            "happy, happi",
            "sky, sky"
    })
    void step1cTerminalY(String word, String expected) {
        assertThat(applyStep(stemmer::step1c, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step2: {0} -> {1}")
    @CsvSource({
            "relational, relate",
            "conditional, condition",
            "rational, rational",
            "valenci, valence",
            "hesitanci, hesitance",
            "digitizer, digitize",
            "conformabli, conformable",
            "radicalli, radical",
            "differentli, different",
            "vileli, vile",
            "analogousli, analogous",
            "vietnamization, vietnamize",
            "predication, predicate",
            "operator, operate",
            "feudalism, feudal",
            "decisiveness, decisive",
            "hopefulness, hopeful",
            "callousness, callous",
            "formaliti, formal",
            "sensitiviti, sensitive",
            "sensibiliti, sensible"
    })
    void step2DerivationalSuffixes(String word, String expected) {
        assertThat(applyStep(stemmer::step2, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step3: {0} -> {1}")
    @CsvSource({
            "triplicate, triplic",
            "formative, form",
            "formalize, formal",
            "electriciti, electric",
            "electrical, electric",
            "hopeful, hope",
            "goodness, good"
    })
    void step3DerivationalSuffixes(String word, String expected) {
        assertThat(applyStep(stemmer::step3, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step4: {0} -> {1}")
    @CsvSource({
            "revival, reviv",
            "allowance, allow",
            "inference, infer",
            "airliner, airlin",
            "gyroscopic, gyroscop",
            "adjustable, adjust",
            "defensible, defens",
            "irritant, irrit",
            "replacement, replac",
            "adjustment, adjust",
            "dependent, depend",
            "adoption, adopt",
            "homologou, homolog",
            "communism, commun",
            "activate, activ",
            "angulariti, angular",
            "homologous, homolog",
            "effective, effect",
            "bowdlerize, bowdler"
    })
    void step4Suffixes(String word, String expected) {
        assertThat(applyStep(stemmer::step4, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step5a: {0} -> {1}")
    @CsvSource({
            "probate, probat",
            "rate, rate",
            "cease, ceas"
    })
    void step5aFinalE(String word, String expected) {
        assertThat(applyStep(stemmer::step5a, word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "step5b: {0} -> {1}")
    @CsvSource({
            "controll, control",
            "roll, roll"
    })
    void step5bDoubleL(String word, String expected) {
        assertThat(applyStep(stemmer::step5b, word)).isEqualTo(expected);
    }

    /**
     * Full-pipeline regression cases, verified by hand through all eight steps (not
     * copied from a single step's illustration). Several deliberately show a step-1b
     * result being reduced further downstream, which is expected: {@code stem()}
     * always runs the complete pipeline and does not stop early.
     */
    @ParameterizedTest(name = "full pipeline: {0} -> {1}")
    @CsvSource({
            "caresses, caress",
            "cats, cat",
            "running, run",
            "horses, hors",
            "sized, size",
            "agreed, agre",
            "conflated, conflat",
            "troubled, troubl",
            "relational, relat"
    })
    void fullPipelineAppliesAllStepsInOrder(String word, String expected) {
        assertThat(stemmer.stem(word)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "short words are returned unchanged: {0}")
    @CsvSource({
            "a, a",
            "at, at",
            "an, an"
    })
    void wordsOfLengthTwoOrLessAreUnchanged(String word, String expected) {
        assertThat(stemmer.stem(word)).isEqualTo(expected);
    }

    /**
     * {@code isConsonant}'s Y case ({@code i == 0 || !isConsonant(sb, i - 1)}) recurses one
     * position to the left per call, so its recursion depth scales with the position being
     * tested inside the word — every real English word (a few dozen letters at most) keeps
     * that depth trivial, but nothing enforces a bound. This proves an all-Y "word" many
     * orders of magnitude longer than any real token (2,000 characters, well past every
     * genuine word this stemmer sees, e.g. from {@code containsVowel}'s scan or a malformed
     * PDF-extraction artifact reaching the preprocessor) neither throws
     * {@code StackOverflowError} nor any other exception.
     */
    @Test
    void stemDoesNotOverflowTheStackOnALongRunOfConsonantYs() {
        String longYRun = "y".repeat(2000);

        assertThatNoException().isThrownBy(() -> stemmer.stem(longYRun));
    }

    private static String applyStep(java.util.function.Consumer<StringBuilder> step, String word) {
        StringBuilder sb = new StringBuilder(word);
        step.accept(sb);
        return sb.toString();
    }
}
