package co.edu.uniquindio.legajo.infrastructure.rest.corpus;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Wire shape of {@code GET /api/v1/corpus/{id}} (TRD §6.6): the article plus its full
 * abstract. {@code abstract} is a reserved Java keyword, so the Java field keeps the
 * domain's own name ({@code abstractText}) and is renamed on the wire with
 * {@link JsonProperty}, the same convention {@code CorpusDocumentJson} already uses for the
 * on-disk {@code corpus.json} schema (TRD §9) — this keeps the API and the data file
 * speaking the same field name for the one field Java cannot spell directly.
 */
public record CorpusDocumentResponse(
        String id, String title, List<String> authors, @JsonProperty("abstract") String abstractText) {

    public CorpusDocumentResponse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(authors, "authors");
        authors = List.copyOf(authors);
        Objects.requireNonNull(abstractText, "abstractText");
    }

    public static CorpusDocumentResponse from(CorpusDocument document) {
        Objects.requireNonNull(document, "document");
        return new CorpusDocumentResponse(
                document.id(), document.title(), document.authors(), document.abstractText());
    }
}
