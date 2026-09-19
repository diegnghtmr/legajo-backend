package co.edu.uniquindio.legajo.infrastructure.corpus;

import co.edu.uniquindio.legajo.corpus.Corpus;

import java.util.List;

/**
 * Wire shape of the corpus document (TRD §9 schema): {@code version}, {@code
 * sourceCount}, {@code corpusSha256}, then {@code documents}, in that declaration
 * order. Package-private: {@link JsonCorpusRepository} is the only class that needs to
 * see this DTO; every other module keeps depending on the domain {@link Corpus} record.
 */
record CorpusJson(String version, int sourceCount, String corpusSha256, List<CorpusDocumentJson> documents) {

    static CorpusJson fromDomain(Corpus corpus) {
        return new CorpusJson(
                corpus.version(),
                corpus.sourceCount(),
                corpus.corpusSha256(),
                corpus.documents().stream().map(CorpusDocumentJson::fromDomain).toList());
    }

    Corpus toDomain() {
        return new Corpus(version, sourceCount, corpusSha256,
                documents.stream().map(CorpusDocumentJson::toDomain).toList());
    }
}
