package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.extraction.AbstractQualityCheck;

/**
 * Formats one line of {@link IngestCli}'s per-document abstract-quality summary
 * (id, extractedBy, character count, ok/suspicious + reason), printed at the end of an
 * ingestion run. Every document is written with {@code manuallyValidated=false};
 * this summary is what makes that mandatory manual review informed
 * instead of the author having to re-read all 20 abstracts blind.
 */
final class IngestQualitySummary {

    private IngestQualitySummary() {
    }

    static String line(CorpusDocument document) {
        String abstractText = document.abstractText();
        AbstractQualityCheck.Verdict verdict = AbstractQualityCheck.assess(abstractText);
        String status = verdict.suspicious()
                ? "suspicious: " + String.join("; ", verdict.reasons())
                : "ok";

        return "  %s [%s] %d chars - %s".formatted(
                document.id(), document.extractedBy(), abstractText.length(), status);
    }
}
