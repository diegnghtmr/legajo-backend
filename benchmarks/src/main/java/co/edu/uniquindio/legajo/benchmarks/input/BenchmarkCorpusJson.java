package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.Corpus;

import java.util.List;

/** Wire shape of {@code data/corpus.json} itself (TRD §9 schema); see {@link BenchmarkCorpusDocumentJson}. */
record BenchmarkCorpusJson(String version, int sourceCount, String corpusSha256, List<BenchmarkCorpusDocumentJson> documents) {

    /**
     * A {@code data/corpus.json} missing the {@code documents} field entirely deserializes
     * with {@code documents == null}: without this guard, {@link #toDomain()} would fail with
     * a bare {@link NullPointerException} from calling {@code documents.stream()}, instead of
     * a message that actually names the missing field.
     */
    BenchmarkCorpusJson {
        if (documents == null) {
            throw new IllegalStateException("corpus JSON is missing the required 'documents' field");
        }
    }

    Corpus toDomain() {
        return new Corpus(version, sourceCount, corpusSha256, documents.stream().map(BenchmarkCorpusDocumentJson::toDomain).toList());
    }
}
