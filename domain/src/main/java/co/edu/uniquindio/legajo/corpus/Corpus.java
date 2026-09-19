package co.edu.uniquindio.legajo.corpus;

import java.util.List;
import java.util.Objects;

/**
 * The reference corpus (TRD §9 schema): a version tag, the number of PDFs found by
 * the ingestion pipeline ({@code sourceCount}, which defines the expected document
 * count for {@link CorpusVerifier}), the corpus-wide hash ({@code corpusSha256},
 * TRD §6.1), and the documents themselves.
 *
 * <p>Like {@link CorpusDocument}, this record validates only structural invariants;
 * {@link CorpusVerifier} owns every business rule (document count, uniqueness,
 * non-empty fields, frozen-hash match, minimum size).
 */
public record Corpus(String version, int sourceCount, String corpusSha256, List<CorpusDocument> documents) {

    public Corpus {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(corpusSha256, "corpusSha256");
        Objects.requireNonNull(documents, "documents");
        documents = List.copyOf(documents);
    }
}
