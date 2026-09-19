package co.edu.uniquindio.legajo.port;

import java.nio.file.Path;

/**
 * Output port for PDF header extraction (TRD §6.1, items 1-2): {@code domain} depends
 * only on this interface. GROBID (primary), PDFBox (reserve) and the primary/fallback
 * chain that combines them are infrastructure adapters (task T4), not part of this
 * module. Delegable per TRD §3.3 — PDF parsing itself is not one of the R-02 algorithms.
 */
public interface PdfMetadataExtractor {

    /**
     * Extracts title, authors and abstract from {@code pdfPath}.
     *
     * @throws PdfExtractionException if this extractor cannot produce any metadata for
     *     {@code pdfPath}; returning a result with a blank abstract (rather than
     *     throwing) is a valid, non-exceptional outcome that callers such as the
     *     fallback chain use to decide whether to try another extractor.
     */
    ExtractedPdfMetadata extract(Path pdfPath);
}
