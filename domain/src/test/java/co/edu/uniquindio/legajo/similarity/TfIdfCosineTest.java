package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@code tfidf-cosine} capability (TRD §6.3, "Fórmulas TF-IDF (fijadas)"; PRD HU-1.3):
 * {@code tf(t,d) = 1 + ln f(t,d)} when {@code f(t,d) > 0}, else 0; {@code idf(t) = ln((1 +
 * N) / (1 + df(t))) + 1}; {@code w(t,d) = tf(t,d) · idf(t)}; vectors L2-normalized; cosine =
 * dot product of the normalized vectors; angle = arccos(cosine) in degrees. {@code df} and
 * {@code N} are computed over the whole corpus passed through {@link SimilarityContext} —
 * never over the two compared documents alone (TRD §6.3, "nunca sobre el par
 * seleccionado").
 *
 * <p>TRD §13 carries no numeric TF-IDF golden, so both goldens below are hand-computed from
 * the fixed formula (not read off a running implementation), using Python's {@code math}
 * module as a calculator (ln/sqrt/acos), and quoted here to 16 significant digits (well
 * past the 1e-9 tolerance TRD §6.3 requires).
 *
 * <pre>
 * Golden 1 — corpus larger than the compared pair (proves df/N are corpus-wide):
 *   D0 = [cat,sat,mat] (= A), D1 = [cat,sat,dog] (= B), D2 = [dog,runs], D3 = [cat,runs]
 *   N = 4; df(cat)=3, df(sat)=2, df(mat)=1, df(dog)=2
 *   idf(cat) = ln(5/4)+1 = 1.2231435513142097
 *   idf(sat) = ln(5/3)+1 = 1.5108256237659907
 *   idf(mat) = ln(5/2)+1 = 1.9162907318741551
 *   idf(dog) = ln(5/3)+1 = 1.5108256237659907  (same df as sat)
 *   f(t,A): cat=1,sat=1,mat=1,dog=0 -&gt; tfA: cat=1,sat=1,mat=1,dog=0 (f=1 -&gt; tf=1+ln1=1)
 *   f(t,B): cat=1,sat=1,mat=0,dog=1 -&gt; tfB: cat=1,sat=1,mat=0,dog=1
 *   wA = {cat:1.2231435513142097, sat:1.5108256237659907, mat:1.9162907318741551, dog:0}
 *   wB = {cat:1.2231435513142097, sat:1.5108256237659907, mat:0, dog:1.5108256237659907}
 *   rawNormA = sqrt(wA·wA) = 2.729623487152801
 *   rawNormB = sqrt(wB·wB) = 2.4619643128967827
 *   cosine = (normalized A)·(normalized B) = 0.5622829957377211
 *   angle = degrees(acos(cosine)) = 55.78617039141408
 *
 * Golden 2 — a repeated token exercises tf(f&gt;1):
 *   Corpus = {A, B} only, N = 2. A = [cat,cat,dog] (f(cat,A)=2), B = [cat,mouse]
 *   df(cat)=2, df(dog)=1, df(mouse)=1
 *   idf(cat) = ln(3/3)+1 = 1.0
 *   idf(dog) = idf(mouse) = ln(3/2)+1 = 1.4054651081081644
 *   tfA(cat) = 1+ln(2) = 1.6931471805599454; tfA(dog) = 1; tfB(cat) = 1; tfB(mouse) = 1
 *   wA = {cat:1.6931471805599454, dog:1.4054651081081644, mouse:0}
 *   wB = {cat:1.0, dog:0, mouse:1.4054651081081644}
 *   rawNormA = 2.200472573141412; rawNormB = 1.7249151196825583
 *   cosine = 0.44607822390362784; angle = degrees(acos(cosine)) = 63.50765612413976
 * </pre>
 */
class TfIdfCosineTest {

    private static final double TOLERANCE = 1e-9;
    private final TfIdfCosine tfIdfCosine = new TfIdfCosine();

    @Test
    void corpusWideGoldenMatchesTheHandComputedCosineAndAngle() {
        SimilarityInput a = input("cat", "sat", "mat");
        SimilarityInput b = input("cat", "sat", "dog");
        SimilarityContext context = corpusContext(
                List.of("cat", "sat", "mat"),
                List.of("cat", "sat", "dog"),
                List.of("dog", "runs"),
                List.of("cat", "runs"));

        SimilarityResult result = tfIdfCosine.compute(a, b, context);

        assertThat(result.normalizedScore()).isCloseTo(0.5622829957377211, within(TOLERANCE));
        assertThat(result.rawValue()).isNotNull();
        assertThat(result.rawValue()).isCloseTo(0.5622829957377211, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void repeatedTokenGoldenExercisesTfOfFrequencyGreaterThanOne() {
        SimilarityInput a = input("cat", "cat", "dog");
        SimilarityInput b = input("cat", "mouse");
        SimilarityContext context = corpusContext(List.of("cat", "cat", "dog"), List.of("cat", "mouse"));

        SimilarityResult result = tfIdfCosine.compute(a, b, context);

        assertThat(result.normalizedScore()).isCloseTo(0.44607822390362784, within(TOLERANCE));
    }

    @Test
    void identicalTokenStreamsScoreOne() {
        SimilarityInput a = input("the", "cat", "sat");
        SimilarityInput b = input("the", "cat", "sat");
        SimilarityContext context = corpusContext(List.of("the", "cat", "sat"), List.of("the", "cat", "sat"));

        SimilarityResult result = tfIdfCosine.compute(a, b, context);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.degenerate()).isFalse();
    }

    @Test
    void bothEmptyTokenStreamsScoreOneAndAreDegenerateWithNullRawValue() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        SimilarityResult result = tfIdfCosine.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(result.rawValue()).isNull();
        assertThat(result.degenerate()).isTrue();
    }

    @Test
    void exactlyOneEmptyTokenStreamScoresZeroAndIsDegenerateWithNullRawValue() {
        SimilarityInput a = input();
        SimilarityInput b = input("the", "cat");

        SimilarityResult result = tfIdfCosine.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.rawValue()).isNull();
        assertThat(result.degenerate()).isTrue();
    }

    @Test
    void theOtherSideEmptyAlsoScoresZeroAndIsDegenerate() {
        SimilarityInput a = input("the", "cat");
        SimilarityInput b = input();

        SimilarityResult result = tfIdfCosine.compute(a, b, SimilarityContext.EMPTY);

        assertThat(result.normalizedScore()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(result.rawValue()).isNull();
        assertThat(result.degenerate()).isTrue();
    }

    @Test
    void computedNanosIsMeasuredAndNonNegative() {
        SimilarityContext context = corpusContext(List.of("a"), List.of("b"));

        SimilarityResult result = tfIdfCosine.compute(input("a"), input("b"), context);

        assertThat(result.computedNanos()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void traceExposesCorpusWideDfAndNNotThePairsOwnCounts() {
        SimilarityInput a = input("cat", "sat", "mat");
        SimilarityInput b = input("cat", "sat", "dog");
        SimilarityContext context = corpusContext(
                List.of("cat", "sat", "mat"),
                List.of("cat", "sat", "dog"),
                List.of("dog", "runs"),
                List.of("cat", "runs"));

        TfIdfCosineTrace trace = (TfIdfCosineTrace) tfIdfCosine.trace(a, b, context).orElseThrow();

        assertThat(trace.algorithmId()).isEqualTo("tfidf-cosine");
        assertThat(trace.corpusSize()).isEqualTo(4);
        // Alphabetical term order (documented on TfIdfCosineTrace): cat, dog, mat, sat.
        assertThat(trace.terms()).extracting(TfIdfTermTrace::term).containsExactly("cat", "dog", "mat", "sat");
        assertThat(trace.terms()).extracting(TfIdfTermTrace::documentFrequency)
                .containsExactly(3, 2, 1, 2); // corpus-wide df, not 1/1/1/1 as it would be over the pair alone.

        TfIdfTermTrace mat = trace.terms().get(2);
        assertThat(mat.term()).isEqualTo("mat");
        assertThat(mat.frequencyA()).isEqualTo(1);
        assertThat(mat.frequencyB()).isEqualTo(0);
        assertThat(mat.tfA()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(mat.tfB()).isEqualTo(0.0);
        assertThat(mat.idf()).isCloseTo(Math.log(5.0 / 2.0) + 1.0, within(TOLERANCE));

        assertThat(trace.dotProduct()).isCloseTo(0.5622829957377211, within(TOLERANCE));
        assertThat(trace.cosine()).isCloseTo(trace.dotProduct(), within(TOLERANCE));
        assertThat(trace.rawNormA()).isCloseTo(2.729623487152801, within(TOLERANCE));
        assertThat(trace.rawNormB()).isCloseTo(2.4619643128967827, within(TOLERANCE));
        assertThat(trace.angleDegrees()).isCloseTo(55.78617039141408, within(TOLERANCE));
    }

    @Test
    void traceOmitsTermsAbsentFromBothCompareDocuments() {
        // "runs" (in D2/D3) is absent from both A and B, so it carries no evidence for this
        // pair and is not listed (interpretation documented on TfIdfCosine/TfIdfCosineTrace).
        SimilarityInput a = input("cat", "sat", "mat");
        SimilarityInput b = input("cat", "sat", "dog");
        SimilarityContext context = corpusContext(
                List.of("cat", "sat", "mat"),
                List.of("cat", "sat", "dog"),
                List.of("dog", "runs"),
                List.of("cat", "runs"));

        TfIdfCosineTrace trace = (TfIdfCosineTrace) tfIdfCosine.trace(a, b, context).orElseThrow();

        assertThat(trace.terms()).extracting(TfIdfTermTrace::term).doesNotContain("runs");
    }

    @Test
    void traceOnBothEmptyInputsHasNoTermsAndTheDegenerateCosineConvention() {
        SimilarityInput a = input();
        SimilarityInput b = input();

        Optional<AlgorithmTrace> traceOptional = tfIdfCosine.trace(a, b, SimilarityContext.EMPTY);

        assertThat(traceOptional).isPresent();
        TfIdfCosineTrace trace = (TfIdfCosineTrace) traceOptional.get();
        assertThat(trace.terms()).isEmpty();
        assertThat(trace.rawNormA()).isEqualTo(0.0);
        assertThat(trace.rawNormB()).isEqualTo(0.0);
        assertThat(trace.cosine()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(trace.angleDegrees()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void traceOnExactlyOneEmptyInputHasTheDegenerateZeroCosineConvention() {
        SimilarityInput a = input();
        SimilarityInput b = input("the", "cat");

        TfIdfCosineTrace trace = (TfIdfCosineTrace) tfIdfCosine.trace(a, b, SimilarityContext.EMPTY).orElseThrow();

        assertThat(trace.terms()).isEmpty();
        assertThat(trace.cosine()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(trace.angleDegrees()).isCloseTo(90.0, within(TOLERANCE));
    }

    private static SimilarityInput input(String... tokens) {
        return new SimilarityInput(String.join(" ", tokens), List.of(tokens));
    }

    @SafeVarargs
    private static SimilarityContext corpusContext(List<String>... corpusTokenStreams) {
        return SimilarityContext.withTfIdfIndex(TfIdfCorpusIndex.from(List.of(corpusTokenStreams)));
    }
}
