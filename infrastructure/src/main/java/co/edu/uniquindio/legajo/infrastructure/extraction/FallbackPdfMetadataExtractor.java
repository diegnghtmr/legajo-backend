package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The primary/fallback chain of TRD §8's GROBID integration row: try {@code primary}
 * first, fall back to {@code fallback} when the primary either throws or comes back
 * with a blank abstract (the extra rule this task adds beyond a plain try/catch), and
 * fail closed with {@link PdfExtractionException} when neither extractor produces a
 * document with a non-blank abstract — a document that would fail {@code
 * verify-corpus}'s non-blank-abstract rule anyway, so it is better to fail the
 * ingestion run loudly than to persist it silently.
 */
public final class FallbackPdfMetadataExtractor implements PdfMetadataExtractor {

    private final PdfMetadataExtractor primary;
    private final PdfMetadataExtractor fallback;

    public FallbackPdfMetadataExtractor(PdfMetadataExtractor primary, PdfMetadataExtractor fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public ExtractedPdfMetadata extract(Path pdfPath) {
        Objects.requireNonNull(pdfPath, "pdfPath");

        ExtractedPdfMetadata primaryResult = tryExtract(primary, pdfPath);
        if (isUsable(primaryResult)) {
            return primaryResult;
        }

        ExtractedPdfMetadata fallbackResult = tryExtract(fallback, pdfPath);
        if (isUsable(fallbackResult)) {
            return fallbackResult;
        }

        throw new PdfExtractionException(
                "Both the primary and the fallback PDF extractor failed (or returned a blank abstract) for " + pdfPath);
    }

    private static boolean isUsable(ExtractedPdfMetadata result) {
        return result != null && !result.hasBlankAbstract();
    }

    private static ExtractedPdfMetadata tryExtract(PdfMetadataExtractor extractor, Path pdfPath) {
        try {
            return extractor.extract(pdfPath);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
