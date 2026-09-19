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

    @Test
    void usesThePrimaryResultWhenItHasANonBlankAbstract(@TempDir Path tempDir) {
        Path pdf = tempDir.resolve("01.pdf");
        AtomicInteger fallbackCalls = new AtomicInteger();
        PdfMetadataExtractor primary = path -> metadata("GROBID", "a usable abstract");
        PdfMetadataExtractor fallback = path -> {
            fallbackCalls.incrementAndGet();
            return metadata("PDFBox", "should not be used");
        };

        ExtractedPdfMetadata result = new FallbackPdfMetadataExtractor(primary, fallback).extract(pdf);

        assertThat(result.extractedBy()).isEqualTo("GROBID");
        assertThat(fallbackCalls.get()).isZero();
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
