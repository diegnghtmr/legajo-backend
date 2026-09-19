package co.edu.uniquindio.legajo.infrastructure.corpus;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire shape of one corpus document (TRD §9 schema). Field order matches the JSON
 * example verbatim; Jackson serializes records in canonical-constructor order, so this
 * declaration order is also the on-disk order. {@code abstract} is a reserved Java
 * keyword, so the domain field {@code abstractText} is renamed on the wire with
 * {@link JsonProperty} rather than forcing an awkward Java name onto the domain.
 */
record CorpusDocumentJson(
        String id,
        String title,
        List<String> authors,
        @JsonProperty("abstract") String abstractText,
        String source,
        String extractedBy,
        boolean manuallyValidated,
        String abstractSha256) {

    static CorpusDocumentJson fromDomain(CorpusDocument document) {
        return new CorpusDocumentJson(
                document.id(),
                document.title(),
                document.authors(),
                document.abstractText(),
                document.source(),
                document.extractedBy(),
                document.manuallyValidated(),
                document.abstractSha256());
    }

    CorpusDocument toDomain() {
        return new CorpusDocument(id, title, authors, abstractText, source, extractedBy,
                manuallyValidated, abstractSha256);
    }
}
