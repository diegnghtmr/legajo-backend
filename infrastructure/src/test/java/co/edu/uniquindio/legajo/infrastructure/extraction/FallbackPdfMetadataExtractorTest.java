package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The primary/fallback chain of TRD §8: try the primary extractor (GROBID in
 * production), fall back to the reserve (PDFBox) when the primary throws or returns a
 * blank abstract, and fail closed with {@link PdfExtractionException} when neither
 * produces a usable (non-blank-abstract) result. Exercised against fakes so the chain's
 * decision logic is tested independently of GROBID or PDFBox.
 */
class FallbackPdfMetadataExtractorTest {

    private static final String GOOD_ABSTRACT =
            "This paper studies several information retrieval methods over a small corpus, "
                    + "comparing precision and recall across five classical algorithms and two neural "
                    + "rerankers under a shared evaluation protocol. We report consistent improvements "
                    + "across all methods when a lightweight preprocessing step is applied beforehand, "
                    + "and we discuss the practical trade-offs of each approach for production systems "
                    + "that must serve results within a tight latency budget while still preserving "
                    + "acceptable ranking quality across a broad range of query lengths and topics.";

    @Test
    void usesThePrimaryResultWhenItHasANonBlankAbstract(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        AtomicInteger fallbackCalls = new AtomicInteger();
        PdfMetadataExtractor primary = path -> metadata("GROBID", GOOD_ABSTRACT);
        PdfMetadataExtractor fallback = path -> {
            fallbackCalls.incrementAndGet();
            return metadata("PDFBox", "should not be used");
        };

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("GROBID");
        assertThat(fallbackCalls.get()).isZero();
    }

    @Test
    void triesTheFallbackWhenThePrimaryAbstractIsSuspiciousAndKeepsTheBetterResult(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("04.pdf");
        String suspicious = GOOD_ABSTRACT + " Recent developments in AI have the potential to support the";
        PdfMetadataExtractor primary = path -> metadata("GROBID", suspicious);
        PdfMetadataExtractor fallback = path -> metadata("PDFBox", GOOD_ABSTRACT);

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("PDFBox");
        assertThat(result.abstractText()).isEqualTo(GOOD_ABSTRACT);
    }

    @Test
    void keepsTheBetterSuspiciousResultWhenBothExtractorsAreSuspicious(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("14.pdf");
        String shortPrimary = "Recent literature underscores the need for teachers to develop AI "
                + "competencies with a recognition of the current lack of well-defined competence "
                + "frameworks. This";
        String shorterFallback = "Recent literature underscores the need for teachers.";
        PdfMetadataExtractor primary = path -> metadata("GROBID", shortPrimary);
        PdfMetadataExtractor fallback = path -> metadata("PDFBox", shorterFallback);

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("GROBID");
        assertThat(result.abstractText()).isEqualTo(shortPrimary);
    }

    @Test
    void fallsBackWhenThePrimaryThrows(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        PdfMetadataExtractor primary = path -> {
            throw new PdfExtractionException("primary failed");
        };
        PdfMetadataExtractor fallback = path -> metadata("PDFBox", "recovered abstract");

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("PDFBox");
        assertThat(result.abstractText()).isEqualTo("recovered abstract");
    }

    @Test
    void fallsBackWhenThePrimaryReturnsABlankAbstract(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        PdfMetadataExtractor primary = path -> metadata("GROBID", "   ");
        PdfMetadataExtractor fallback = path -> metadata("PDFBox", "recovered abstract");

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("PDFBox");
    }

    @Test
    void failsClosedWhenBothExtractorsThrow(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        PdfMetadataExtractor primary = path -> {
            throw new PdfExtractionException("primary failed");
        };
        PdfMetadataExtractor fallback = path -> {
            throw new PdfExtractionException("fallback failed");
        };

        assertThatThrownBy(() -> new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf))
                .isInstanceOf(PdfExtractionException.class);
    }

    @Test
    void failsClosedWhenBothExtractorsReturnABlankAbstract(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        PdfMetadataExtractor primary = path -> metadata("GROBID", "");
        PdfMetadataExtractor fallback = path -> metadata("PDFBox", "");

        assertThatThrownBy(() -> new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf))
                .isInstanceOf(PdfExtractionException.class);
    }

    private static ExtractedPdfMetadata metadata(String extractedBy, String abstractText) {
        return new ExtractedPdfMetadata("Title", List.of("Author"), abstractText, extractedBy);
    }
}
