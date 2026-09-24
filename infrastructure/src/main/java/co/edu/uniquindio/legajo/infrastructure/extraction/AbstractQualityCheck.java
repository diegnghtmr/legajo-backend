package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An abstract-completeness/contamination gate for the ingestion chain, added after two
 * real regressions were found in the reference corpus while
 * GROBID's {@code processHeaderDocument} was the only source consulted:
 *
 * <ul>
 *   <li>{@code d04}: the extracted abstract is the correct length, but a foreign
 *       sentence from the body ("Recent developments in AI have the potential to
 *       support the") is appended after the real ending, so the text neither ends in
 *       terminal punctuation nor on a complete clause.</li>
 *   <li>{@code d14}: the extracted abstract is cut to 161 characters on a narrow
 *       two-column layout, ending mid-sentence with no terminal punctuation either.</li>
 * </ul>
 *
 * <p>{@link FallbackPdfMetadataExtractor} already treated a <em>blank</em> abstract as
 * "try the other extractor"; this class extends that same idea to a non-blank abstract
 * that still looks wrong, without ever hard-coding anything about a specific document,
 * so the ingestion pipeline stays reusable over any PDF folder.
 *
 * <p>A result is flagged {@link Verdict#suspicious()} when any of three independent,
 * generic signals fires:
 *
 * <ol>
 *   <li><b>Too short.</b> Shorter than {@link #MIN_ABSTRACT_LENGTH} characters. This
 *       threshold is not a guess: in the reference corpus, all 18 correctly extracted
 *       abstracts are between 902 and 1817 characters, while both known-truncated
 *       results measured 161 and 372 characters — comfortably below a 500-character
 *       cut usable for any future PDF folder, since a legitimate abstract this short
 *       would still be extremely terse (well under 80 words).</li>
 *   <li><b>No terminal punctuation.</b> The trimmed text does not end in {@code . ! ?}
 *       (optionally followed by a closing quote/parenthesis).</li>
 *   <li><b>Ends mid-clause.</b> The last word is a generic function word (article,
 *       preposition, conjunction or determiner) that essentially never legitimately
 *       ends a complete English sentence — the exact pattern GROBID produced for
 *       {@code d04} ("...support the").</li>
 * </ol>
 *
 * <p>A suspicious result is never discarded on its own: {@link #pickBetter} lets the
 * ingestion chain keep the better of two candidates, and the caller is the one that
 * decides whether "better but still suspicious" is good enough to persist (surfaced to
 * the author through the ingestion CLI's quality summary) or whether both extractors
 * failed outright, which remains the only case this pipeline fails closed for.
 */
public final class AbstractQualityCheck {

    /** See the class Javadoc for how this threshold was measured. */
    public static final int MIN_ABSTRACT_LENGTH = 500;

    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile(".*[.!?][\"'\\u201d\\u2019)]?$", Pattern.DOTALL);
    private static final Pattern TRAILING_WORD = Pattern.compile("([\\p{L}]+)[\\p{Punct}]*$");

    // Generic English function words (articles, prepositions, conjunctions, determiners)
    // that essentially never legitimately end a complete sentence. Not tied to any
    // document; this is a linguistic heuristic, distinct from the stopword list used by
    // the preprocessing pipeline for a different purpose.
    private static final Set<String> DANGLING_CONNECTOR_WORDS = Set.of(
            "the", "a", "an",
            "in", "on", "at", "by", "for", "with", "of", "to", "from", "into", "onto",
            "upon", "under", "over", "about", "between", "among", "through", "during", "before", "after",
            "and", "or", "but", "nor", "so", "yet", "because", "although", "while", "if", "unless",
            "this", "that", "these", "those", "its", "their", "his", "her", "our", "your", "my");

    private AbstractQualityCheck() {
    }

    /** The outcome of {@link #assess(String)}: whether the text looks suspicious, and why. */
    public record Verdict(boolean suspicious, List<String> reasons) {
        public Verdict {
            reasons = List.copyOf(reasons);
        }
    }

    public static Verdict assess(String abstractText) {
        Objects.requireNonNull(abstractText, "abstractText");
        String trimmed = abstractText.trim();
        List<String> reasons = new ArrayList<>();

        if (trimmed.length() < MIN_ABSTRACT_LENGTH) {
            reasons.add("abstract is %d character(s), shorter than the %d-character quality threshold"
                    .formatted(trimmed.length(), MIN_ABSTRACT_LENGTH));
        }
        if (!trimmed.isEmpty() && !TERMINAL_PUNCTUATION.matcher(trimmed).matches()) {
            reasons.add("abstract does not end in terminal punctuation (. ! ?)");
        }
        String trailingWord = trailingWordOf(trimmed);
        if (trailingWord != null && DANGLING_CONNECTOR_WORDS.contains(trailingWord.toLowerCase(Locale.ROOT))) {
            reasons.add("abstract ends mid-clause (dangling trailing word \"%s\")".formatted(trailingWord));
        }

        return new Verdict(!reasons.isEmpty(), reasons);
    }

    /**
     * Picks the better of two extraction candidates for the same document: a
     * non-suspicious result always wins over a suspicious one, regardless of length
     * (this is what makes {@code d04}'s clean-but-shorter {@code
     * processFulltextDocument} abstract beat the longer, contaminated {@code
     * processHeaderDocument} one). When both are equally suspicious (or both are
     * fine), the longer trimmed text wins, on the assumption that a truncated abstract
     * is shorter than the complete one it was cut from.
     */
    public static ExtractedPdfMetadata pickBetter(ExtractedPdfMetadata a, ExtractedPdfMetadata b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        boolean aSuspicious = assess(a.abstractText()).suspicious();
        boolean bSuspicious = assess(b.abstractText()).suspicious();
        if (aSuspicious != bSuspicious) {
            return aSuspicious ? b : a;
        }
        return a.abstractText().trim().length() >= b.abstractText().trim().length() ? a : b;
    }

    private static String trailingWordOf(String trimmed) {
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = TRAILING_WORD.matcher(trimmed);
        return matcher.find() ? matcher.group(1) : null;
    }
}
