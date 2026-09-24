package co.edu.uniquindio.legajo.port;

import java.util.List;
import java.util.Objects;

/**
 * The header metadata a {@link PdfMetadataExtractor} pulls out of one PDF: title,
 * authors, abstract, and which extractor produced it ({@code
 * "GROBID"} or {@code "PDFBox"}), which the ingestion use case persists verbatim as
 * {@code CorpusDocument.extractedBy}.
 *
 * <p>This record validates only structural invariants (no null field, an immutable
 * {@code authors} list); an extractor is allowed to return a blank {@code
 * abstractText} — {@link #hasBlankAbstract()} is exactly the signal the fallback chain
 * (infrastructure) uses to decide whether to try the next extractor.
 */
public record ExtractedPdfMetadata(String title, List<String> authors, String abstractText, String extractedBy) {

    public ExtractedPdfMetadata {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authors, "authors");
        authors = List.copyOf(authors);
        Objects.requireNonNull(abstractText, "abstractText");
        Objects.requireNonNull(extractedBy, "extractedBy");
    }

    public boolean hasBlankAbstract() {
        return abstractText.isBlank();
    }
}
