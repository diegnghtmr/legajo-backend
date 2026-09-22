package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.Corpus;

import java.util.List;

/** Wire shape of {@code data/corpus.json} itself (TRD §9 schema); see {@link BenchmarkCorpusDocumentJson}. */
record BenchmarkCorpusJson(String version, int sourceCount, String corpusSha256, List<BenchmarkCorpusDocumentJson> documents) {

    Corpus toDomain() {
        return new Corpus(version, sourceCount, corpusSha256, documents.stream().map(BenchmarkCorpusDocumentJson::toDomain).toList());
    }
}
