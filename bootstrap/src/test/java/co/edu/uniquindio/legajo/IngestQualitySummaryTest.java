package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ingest}'s per-document quality summary line (id, extractedBy, character
 * count, ok/suspicious + reason), printed at the end of a run so the author's manual
 * validation is informed about which abstracts need a closer look.
 */
class IngestQualitySummaryTest {

    private static final String GOOD_ABSTRACT =
            "This paper studies several information retrieval methods over a small corpus, "
                    + "comparing precision and recall across five classical algorithms and two neural "
                    + "rerankers under a shared evaluation protocol. We report consistent improvements "
                    + "across all methods when a lightweight preprocessing step is applied beforehand, "
                    + "and we discuss the practical trade-offs of each approach for production systems "
                    + "that must serve results within a tight latency budget while still preserving "
                    + "acceptable ranking quality across a broad range of query lengths and topics.";

    @Test
    void reportsOkForANonSuspiciousAbstract() {
        CorpusDocument document = document("d01", "GROBID", GOOD_ABSTRACT);

        String line = IngestQualitySummary.line(document);

        assertThat(line)
                .contains("d01")
                .contains("GROBID")
                .contains(String.valueOf(GOOD_ABSTRACT.length()))
                .contains("ok")
                .doesNotContain("suspicious");
    }

    @Test
    void reportsSuspiciousWithReasonsForATruncatedAbstract() {
        String truncated = "Recent literature underscores the need for teachers to develop AI "
                + "competencies with a recognition of the current lack of well-defined competence "
                + "frameworks. This";
        CorpusDocument document = document("d14", "GROBID", truncated);

        String line = IngestQualitySummary.line(document);

        assertThat(line)
                .contains("d14")
                .contains("suspicious")
                .contains("shorter than");
    }

    private static CorpusDocument document(String id, String extractedBy, String abstractText) {
        return new CorpusDocument(id, "Title", List.of("Author"), abstractText, "data/pdfs/x.pdf", extractedBy,
                false, "sha");
    }
}
