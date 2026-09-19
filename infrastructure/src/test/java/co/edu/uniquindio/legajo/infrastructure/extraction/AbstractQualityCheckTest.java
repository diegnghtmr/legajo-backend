package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4b: the abstract-quality gate that lets the ingestion chain notice a contaminated or
 * truncated abstract instead of only a blank one (TRD §8 fail-closed rule still applies
 * only when every extractor comes back unusable). Verified against the two real
 * regressions found in the reference corpus:
 *
 * <ul>
 *   <li>{@code d04}: GROBID's header abstract is a normal length but has a foreign
 *       sentence appended after the real ending, so it neither ends in terminal
 *       punctuation nor on a complete clause ("...support the").</li>
 *   <li>{@code d14}: GROBID's header abstract is cut to 161 characters ("...well-defined
 *       competence frameworks. This") on a narrow two-column layout.</li>
 * </ul>
 *
 * <p>The 500-character length threshold is not a guess: every one of the 18 correctly
 * extracted abstracts in the reference corpus is between 902 and 1817 characters, while
 * both known-truncated results (161 and 372 characters) fall far below it.
 */
class AbstractQualityCheckTest {

    private static final String COMPLETE_ABSTRACT =
            "This paper studies several information retrieval methods over a small corpus, "
                    + "comparing precision and recall across five classical algorithms and two neural "
                    + "rerankers under a shared evaluation protocol. We report consistent improvements "
                    + "across all methods when a lightweight preprocessing step is applied beforehand, "
                    + "and we discuss the practical trade-offs of each approach for production systems "
                    + "that must serve results within a tight latency budget while still preserving "
                    + "acceptable ranking quality across a broad range of query lengths and topics.";

    @Test
    void aCompleteAbstractIsNotSuspicious() {
        assertThat(COMPLETE_ABSTRACT.length()).isGreaterThanOrEqualTo(AbstractQualityCheck.MIN_ABSTRACT_LENGTH);

        AbstractQualityCheck.Verdict verdict = AbstractQualityCheck.assess(COMPLETE_ABSTRACT);

        assertThat(verdict.suspicious()).isFalse();
        assertThat(verdict.reasons()).isEmpty();
    }

    @Test
    void anAbstractShorterThanTheThresholdIsSuspicious() {
        String shortAbstract = "Recent literature underscores the need for teachers to develop AI "
                + "competencies with a recognition of the current lack of well-defined competence "
                + "frameworks. This";

        AbstractQualityCheck.Verdict verdict = AbstractQualityCheck.assess(shortAbstract);

        assertThat(verdict.suspicious()).isTrue();
        assertThat(verdict.reasons()).anyMatch(reason -> reason.contains("shorter than"));
    }

    @Test
    void anAbstractNotEndingInTerminalPunctuationIsSuspicious() {
        String noTerminalPunctuation = COMPLETE_ABSTRACT.substring(0, COMPLETE_ABSTRACT.length() - 1) + " word";

        AbstractQualityCheck.Verdict verdict = AbstractQualityCheck.assess(noTerminalPunctuation);

        assertThat(verdict.suspicious()).isTrue();
        assertThat(verdict.reasons()).anyMatch(reason -> reason.contains("terminal punctuation"));
    }

    @Test
    void anAbstractEndingOnADanglingConnectorWordIsSuspicious() {
        String danglingEnding = COMPLETE_ABSTRACT + " Recent developments in AI have the potential to support the";

        AbstractQualityCheck.Verdict verdict = AbstractQualityCheck.assess(danglingEnding);

        assertThat(verdict.suspicious()).isTrue();
        assertThat(verdict.reasons()).anyMatch(reason -> reason.contains("mid-clause"));
    }

    @Test
    void pickBetterKeepsTheNonSuspiciousCandidateRegardlessOfLength() {
        ExtractedPdfMetadata suspiciousButLonger =
                metadata("GROBID", COMPLETE_ABSTRACT + " Recent developments in AI have the potential to support the");
        ExtractedPdfMetadata clean = metadata("GROBID", COMPLETE_ABSTRACT);

        ExtractedPdfMetadata result = AbstractQualityCheck.pickBetter(suspiciousButLonger, clean);

        assertThat(result).isEqualTo(clean);
    }

    @Test
    void pickBetterPrefersTheLongerCandidateWhenBothAreSuspicious() {
        ExtractedPdfMetadata short1 = metadata("GROBID", "Too short to be a real abstract.");
        ExtractedPdfMetadata short2 = metadata("PDFBox", "Even shorter.");

        ExtractedPdfMetadata result = AbstractQualityCheck.pickBetter(short1, short2);

        assertThat(result).isEqualTo(short1);
    }

    @Test
    void pickBetterPrefersTheLongerCandidateWhenBothAreNonSuspicious() {
        ExtractedPdfMetadata longer = metadata("GROBID", COMPLETE_ABSTRACT + " " + COMPLETE_ABSTRACT);
        ExtractedPdfMetadata shorter = metadata("PDFBox", COMPLETE_ABSTRACT);

        ExtractedPdfMetadata result = AbstractQualityCheck.pickBetter(longer, shorter);

        assertThat(result).isEqualTo(longer);
    }

    private static ExtractedPdfMetadata metadata(String extractedBy, String abstractText) {
        return new ExtractedPdfMetadata("Title", List.of("Author"), abstractText, extractedBy);
    }
}
