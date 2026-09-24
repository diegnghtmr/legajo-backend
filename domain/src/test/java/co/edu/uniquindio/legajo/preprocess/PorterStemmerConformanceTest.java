package co.edu.uniquindio.legajo.preprocess;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance oracle for {@link PorterStemmer}: stems every word of Porter's own
 * official reference vocabulary and asserts the result matches the official
 * reference stemmer's output, line for line.
 *
 * <p>{@code porter-voc.txt} and {@code porter-output.txt} are an unmodified copy of
 * {@code voc.txt} and {@code output.txt} distributed at
 * <a href="https://tartarus.org/martin/PorterStemmer/voc.txt">tartarus.org/martin/PorterStemmer/voc.txt</a>
 * and
 * <a href="https://tartarus.org/martin/PorterStemmer/output.txt">tartarus.org/martin/PorterStemmer/output.txt</a>
 * — 23,531 line-aligned input words and the stems produced by Porter's own
 * reference implementation. They are the closest thing to an authoritative oracle
 * for this algorithm: Porter is required to be hand-written, so there
 * is no library to defer correctness to, and the paper's worked examples alone
 * (already covered by {@link PorterStemmerTest}) are too few to catch rule
 * ordering and edge-case bugs. This test is the regression guard for those.
 */
class PorterStemmerConformanceTest {

    private static final String VOCABULARY_RESOURCE = "/preprocess/porter-voc.txt";
    private static final String EXPECTED_STEMS_RESOURCE = "/preprocess/porter-output.txt";
    private static final int MAX_REPORTED_MISMATCHES = 25;

    private final PorterStemmer stemmer = new PorterStemmer();

    @Test
    void stemsTheOfficialReferenceVocabularyExactly() throws IOException {
        List<String> words = readLines(VOCABULARY_RESOURCE);
        List<String> expectedStems = readLines(EXPECTED_STEMS_RESOURCE);
        assertThat(words).hasSameSizeAs(expectedStems);

        List<String> mismatches = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String expected = expectedStems.get(i);
            String actual = stemmer.stem(word);
            if (!actual.equals(expected)) {
                mismatches.add(word + " -> " + actual + "  expected " + expected);
            }
        }

        if (!mismatches.isEmpty()) {
            String report = mismatches.stream()
                    .limit(MAX_REPORTED_MISMATCHES)
                    .reduce("", (acc, line) -> acc + "\n" + line);
            org.junit.jupiter.api.Assertions.fail(
                    "mismatches=" + mismatches.size() + " of " + words.size()
                            + " words (first " + Math.min(MAX_REPORTED_MISMATCHES, mismatches.size())
                            + " shown):" + report);
        }
    }

    private static List<String> readLines(String resource) throws IOException {
        try (InputStream in = PorterStemmerConformanceTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing test resource: " + resource);
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }
}
