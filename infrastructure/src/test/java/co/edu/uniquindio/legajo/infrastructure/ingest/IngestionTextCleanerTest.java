package co.edu.uniquindio.legajo.infrastructure.ingest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for the fixed ingestion cleaning step — applied to
 * raw PDF text before it is persisted, and distinct from the later text preprocessing
 * pipeline: hyphen join across a line break, NFKC normalization of ligatures, collapse
 * of in-paragraph line breaks, and collapse of whitespace runs.
 */
class IngestionTextCleanerTest {

    @Test
    void joinsAHyphenatedWordAcrossALineBreak() {
        String raw = "This is infor-\nmation split across lines.";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("This is information split across lines.");
    }

    @Test
    void joinsAHyphenatedWordAcrossALineBreakWithTrailingSpacesOnTheBrokenLine() {
        String raw = "co-  \n  operation";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("cooperation");
    }

    @Test
    void normalizesLigaturesToNfkc() {
        // U+FB01 LATIN SMALL LIGATURE FI -> "fi"
        String raw = "The ﬁrst result.";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("The first result.");
    }

    @Test
    void collapsesInParagraphLineBreaksToASingleSpace() {
        String raw = "Line one\nline two\nline three";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("Line one line two line three");
    }

    @Test
    void collapsesRunsOfWhitespaceToASingleSpace() {
        String raw = "Too    many     spaces\tand\ttabs";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("Too many spaces and tabs");
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(IngestionTextCleaner.clean("  \n  padded text  \n  ")).isEqualTo("padded text");
    }

    @Test
    void rejectsNullInput() {
        assertThatNullPointerException().isThrownBy(() -> IngestionTextCleaner.clean(null));
    }

    @Test
    void combinesAllStepsInOrderOnRealisticRawText() {
        String raw = "  A study of infor-\nmation reﬁnement\n\nacross   multiple\nlines.  ";

        assertThat(IngestionTextCleaner.clean(raw)).isEqualTo("A study of information refinement across multiple lines.");
    }
}
