package co.edu.uniquindio.legajo.infrastructure.ingest;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The ingestion cleaning delegable step fixed by TRD §6.1, item 3: applied to the raw
 * text extracted from a PDF, always, before it is persisted — distinct from and prior
 * to the TRD §6.2 preprocessing pipeline. Delegable per TRD §3.3 (it is glue code, not
 * one of the R-02 algorithms), but hand-written here because it is a handful of regex
 * substitutions and does not warrant a third-party text-cleaning dependency.
 *
 * <p>Steps, applied in order:
 * <ol>
 *   <li>Hyphen join: a word broken across a line by a hyphen ("infor-\nmation") is
 *       rejoined before anything else runs, so the later newline-collapse step does not
 *       turn it into "infor- mation" instead.</li>
 *   <li>Unicode NFKC normalization, which folds typographic ligatures (e.g. U+FB01
 *       "ﬁ") into their plain-letter decomposition ("fi").</li>
 *   <li>Collapse of in-paragraph line breaks to a single space — PDF text extraction
 *       wraps lines mid-sentence; this pipeline treats every newline in an abstract as
 *       a wrap, not a real paragraph break.</li>
 *   <li>Collapse of any remaining run of whitespace to a single space, then a final
 *       trim.</li>
 * </ol>
 */
public final class IngestionTextCleaner {

    private static final Pattern HYPHEN_LINE_BREAK = Pattern.compile("(\\p{L})-[ \\t]*\\r?\\n[ \\t]*(\\p{L})");
    private static final Pattern LINE_BREAKS = Pattern.compile("\\r?\\n+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private IngestionTextCleaner() {
    }

    public static String clean(String raw) {
        Objects.requireNonNull(raw, "raw");

        String hyphenJoined = HYPHEN_LINE_BREAK.matcher(raw).replaceAll("$1$2");
        String nfkc = Normalizer.normalize(hyphenJoined, Normalizer.Form.NFKC);
        String noLineBreaks = LINE_BREAKS.matcher(nfkc).replaceAll(" ");
        String collapsed = WHITESPACE_RUN.matcher(noLineBreaks).replaceAll(" ");
        return collapsed.trim();
    }
}
