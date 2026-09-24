package co.edu.uniquindio.legajo.port;

/**
 * Thrown when a {@link PdfMetadataExtractor} cannot produce metadata for a PDF, and
 * when the primary/fallback chain (infrastructure) exhausts every extractor without a
 * usable result — the "fail closed if both fail" rule for the GROBID integration
 * chain.
 */
public class PdfExtractionException extends RuntimeException {

    public PdfExtractionException(String message) {
        super(message);
    }

    public PdfExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
