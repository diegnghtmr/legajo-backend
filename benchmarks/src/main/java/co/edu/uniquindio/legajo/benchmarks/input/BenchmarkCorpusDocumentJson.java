package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire shape of one {@code data/corpus.json} document, mirroring
 * {@code infrastructure}'s own {@code CorpusDocumentJson} (this module cannot depend on
 * {@code :infrastructure} per the module map, so it reads the same file with its own,
 * benchmarks-only copy of the DTO). {@code abstract} is a reserved Java keyword, renamed on
 * the wire with {@link JsonProperty}.
 */
record BenchmarkCorpusDocumentJson(
        String id,
        String title,
        List<String> authors,
        @JsonProperty("abstract") String abstractText,
        String source,
        String extractedBy,
        boolean manuallyValidated,
        String abstractSha256) {

    CorpusDocument toDomain() {
        return new CorpusDocument(id, title, authors, abstractText, source, extractedBy, manuallyValidated,
                abstractSha256);
    }
}
