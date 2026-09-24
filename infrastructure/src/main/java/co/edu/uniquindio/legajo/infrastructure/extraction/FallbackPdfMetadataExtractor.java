package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The primary/fallback chain of the GROBID integration: try {@code primary}
 * first, fall back to {@code fallback} when the primary either throws or comes back
 * with a blank abstract (the extra rule this task adds beyond a plain try/catch), and
 * fail closed with {@link PdfExtractionException} when neither extractor produces a
 * document with a non-blank abstract — a document that would fail {@code
 * verify-corpus}'s non-blank-abstract rule anyway, so it is better to fail the
 * ingestion run loudly than to persist it silently.
 *
 * <p>A non-blank abstract is no longer automatically "usable" on its own. When the
 * primary's abstract is {@linkplain AbstractQualityCheck suspicious} (too short,
 * contaminated, or cut mid-clause), this chain also tries the fallback and keeps the
 * better of the two ({@link AbstractQualityCheck#pickBetter}) instead of committing to
 * a possibly-truncated primary result just because it is non-blank. Failing closed is
 * still reserved for the case neither extractor produces any usable text at all — a
 * result that is merely suspicious, on both sides, is still persisted (as the better of
 * the two) and surfaced to the author through the ingestion CLI's quality summary for
 * manual validation.
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

        Attempt primaryAttempt = tryExtract(primary, pdfPath, "primary");
        if (primaryAttempt.usable() && !isSuspicious(primaryAttempt.result())) {
            return primaryAttempt.result();
        }

        Attempt fallbackAttempt = tryExtract(fallback, pdfPath, "fallback");

        if (!primaryAttempt.usable() && !fallbackAttempt.usable()) {
            PdfExtractionException failure = new PdfExtractionException(
                    "Both the primary and the fallback PDF extractor failed (or returned a blank abstract) for "
                            + pdfPath);
            primaryAttempt.attachTo(failure);
            fallbackAttempt.attachTo(failure);
            throw failure;
        }
        if (!primaryAttempt.usable()) {
            return fallbackAttempt.result();
        }
        if (!fallbackAttempt.usable()) {
            return primaryAttempt.result();
        }
        return AbstractQualityCheck.pickBetter(primaryAttempt.result(), fallbackAttempt.result());
    }

    private static boolean isUsable(ExtractedPdfMetadata result) {
        return result != null && !result.hasBlankAbstract();
    }

    private static boolean isSuspicious(ExtractedPdfMetadata result) {
        return AbstractQualityCheck.assess(result.abstractText()).suspicious();
    }

    private static Attempt tryExtract(PdfMetadataExtractor extractor, Path pdfPath, String role) {
        try {
            return new Attempt(role, extractor.extract(pdfPath), null);
        } catch (RuntimeException e) {
            return new Attempt(role, null, e);
        }
    }

    /**
     * The outcome of trying one extractor, kept instead of discarding it, so an
     * otherwise-undiagnosable failure stays traceable: when both extractors ultimately fail, {@link
     * #attachTo} records each attempt's cause — either the exception it threw or a
     * note that it returned a blank abstract — as a {@linkplain
     * Throwable#addSuppressed(Throwable) suppressed exception} on the final fail-closed
     * {@link PdfExtractionException}, so a real ingestion failure stays diagnosable
     * instead of collapsing into one generic message.
     */
    private record Attempt(String role, ExtractedPdfMetadata result, RuntimeException failure) {

        boolean usable() {
            return isUsable(result);
        }

        void attachTo(PdfExtractionException finalFailure) {
            if (failure != null) {
                finalFailure.addSuppressed(failure);
            } else if (!usable()) {
                finalFailure.addSuppressed(
                        new PdfExtractionException(role + " extractor returned a blank abstract"));
            }
        }
    }
}
