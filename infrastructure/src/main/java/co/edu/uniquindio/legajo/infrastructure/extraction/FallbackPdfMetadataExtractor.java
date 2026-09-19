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
 *
 * <p>T4b: a non-blank abstract is no longer automatically "usable" on its own. When the
 * primary's abstract is {@linkplain AbstractQualityCheck suspicious} (too short,
 * contaminated, or cut mid-clause), this chain also tries the fallback and keeps the
 * better of the two ({@link AbstractQualityCheck#pickBetter}) instead of committing to
 * a possibly-truncated primary result just because it is non-blank. Failing closed is
 * still reserved for the case neither extractor produces any usable text at all — a
 * result that is merely suspicious, on both sides, is still persisted (as the better of
 * the two) and surfaced to the author through the ingestion CLI's quality summary for
 * manual validation, per TRD §6.1 item 5.
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
        boolean primaryUsable = isUsable(primaryResult);
        if (primaryUsable && !isSuspicious(primaryResult)) {
            return primaryResult;
        }

        ExtractedPdfMetadata fallbackResult = tryExtract(fallback, pdfPath);
        boolean fallbackUsable = isUsable(fallbackResult);

        if (!primaryUsable && !fallbackUsable) {
            throw new PdfExtractionException(
                    "Both the primary and the fallback PDF extractor failed (or returned a blank abstract) for " + pdfPath);
        }
        if (!primaryUsable) {
            return fallbackResult;
        }
        if (!fallbackUsable) {
            return primaryResult;
        }
        return AbstractQualityCheck.pickBetter(primaryResult, fallbackResult);
    }

    private static boolean isUsable(ExtractedPdfMetadata result) {
        return result != null && !result.hasBlankAbstract();
    }

    private static boolean isSuspicious(ExtractedPdfMetadata result) {
        return AbstractQualityCheck.assess(result.abstractText()).suspicious();
    }

    private static ExtractedPdfMetadata tryExtract(PdfMetadataExtractor extractor, Path pdfPath) {
        try {
            return extractor.extract(pdfPath);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
