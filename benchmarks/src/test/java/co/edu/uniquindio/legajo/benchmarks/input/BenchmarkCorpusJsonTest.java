package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link BenchmarkCorpusJson}'s guard against a missing/null {@code documents}
 * field: {@link BenchmarkCorpusJson#toDomain()} used to call {@code documents.stream()}
 * directly, so a {@code data/corpus.json} missing that field entirely failed with a bare
 * {@link NullPointerException} instead of a message naming what is actually wrong with the
 * file.
 */
class BenchmarkCorpusJsonTest {

    @Test
    void rejectsNullDocumentsInsteadOfThrowingANullPointerException() {
        assertThatThrownBy(() -> new BenchmarkCorpusJson("v1", 0, "deadbeef", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("documents");
    }

    @Test
    void rejectsARealJsonFixtureMissingTheDocumentsField() {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        String jsonMissingDocuments = """
                { "version": "v1", "sourceCount": 0, "corpusSha256": "deadbeef" }
                """;

        // Jackson wraps the record's compact-constructor exception in its own
        // ValueInstantiationException; what matters is that the original clear message survives
        // in it, unlike the bare NullPointerException `documents.stream()` used to throw.
        assertThatThrownBy(() -> jsonMapper.readValue(jsonMissingDocuments, BenchmarkCorpusJson.class))
                .isNotInstanceOf(NullPointerException.class)
                .hasMessageContaining("documents");
    }
}
