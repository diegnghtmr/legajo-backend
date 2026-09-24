package co.edu.uniquindio.legajo.preprocess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Five-step text preprocessing pipeline, pure Java with no framework
 * dependency: NFC normalization, lowercasing with {@link Locale#ROOT}, regex
 * tokenization, English stopword removal, and optional Porter stemming (default off).
 *
 * <p>No token-length filter is applied: two-letter domain tokens such as "ai" or "ml"
 * survive. The five steps feed only the four classic similarity capabilities and the
 * {@code tfidf-cosine} representation; the embedding capabilities consume the raw
 * abstract instead, which is why this class returns tokens rather
 * than reaching further into similarity concerns.
 */
public final class TextPreprocessor {

    private static final String STOPWORDS_RESOURCE = "/preprocess/stopwords-en.txt";
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final Set<String> stopwords;
    private final PorterStemmer stemmer = new PorterStemmer();

    public TextPreprocessor() {
        this(loadDefaultStopwords());
    }

    /** Package-visible constructor for tests that need a controlled stopword set. */
    TextPreprocessor(Set<String> stopwords) {
        this.stopwords = Set.copyOf(Objects.requireNonNull(stopwords, "stopwords"));
    }

    /** Preprocesses {@code text} with Porter stemming disabled (the v1 default). */
    public PreprocessedText preprocess(String text) {
        return preprocess(text, false);
    }

    /**
     * Runs the five preprocessing steps over {@code text}: NFC, lowercase, tokenize, remove
     * stopwords, and — when {@code stemming} is {@code true} — apply the hand-written
     * Porter (1980) stemmer uniformly to the remaining tokens.
     */
    public PreprocessedText preprocess(String text, boolean stemming) {
        Objects.requireNonNull(text, "text");

        String normalized = normalizeAndLowercase(text);
        List<String> tokens = tokenize(normalized);
        List<String> withoutStopwords = removeStopwords(tokens);
        List<String> finalTokens = stemming ? stem(withoutStopwords) : withoutStopwords;

        return new PreprocessedText(finalTokens, stemming);
    }

    /** Steps 1–2: Unicode NFC normalization followed by {@link Locale#ROOT} lowercasing. */
    static String normalizeAndLowercase(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** Step 3: tokenize on maximal runs of letters and digits, discarding punctuation. */
    private static List<String> tokenize(String normalized) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /** Step 4: drop tokens present in the versioned English stopword list. */
    private List<String> removeStopwords(List<String> tokens) {
        return tokens.stream().filter(token -> !stopwords.contains(token)).toList();
    }

    /** Step 5: optional Porter stemming, applied uniformly to every surviving token. */
    private List<String> stem(List<String> tokens) {
        return tokens.stream().map(stemmer::stem).toList();
    }

    private static Set<String> loadDefaultStopwords() {
        try (InputStream in = TextPreprocessor.class.getResourceAsStream(STOPWORDS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource: " + STOPWORDS_RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load " + STOPWORDS_RESOURCE, e);
        }
    }
}
