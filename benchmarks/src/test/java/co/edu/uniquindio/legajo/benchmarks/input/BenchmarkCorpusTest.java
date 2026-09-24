package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.corpus.Corpus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the real-corpus loader and its token-stream/pool derivations, used by the
 * classic-pairwise and clustering SLO benchmarks and by the pairwise-curve benchmarks'
 * synthetic token pool. Written before {@link BenchmarkCorpus} exists (strict TDD).
 */
class BenchmarkCorpusTest {

    @Test
    void loadsTheVersionedReferenceCorpus() {
        Corpus corpus = BenchmarkCorpus.load();

        // Self-consistency, not a hardcoded document count: this must hold for whatever
        // corpus is currently versioned, and verify-corpus's own floor is n >= 3.
        assertThat(corpus.documents()).hasSize(corpus.sourceCount());
        assertThat(corpus.documents().size()).isGreaterThanOrEqualTo(3);
        assertThat(corpus.corpusSha256()).isNotBlank();
    }

    @Test
    void preprocessesEveryDocumentIntoANonEmptyTokenStream() {
        Corpus corpus = BenchmarkCorpus.load();

        List<List<String>> tokenStreams = BenchmarkCorpus.preprocessedTokenStreams(corpus);

        assertThat(tokenStreams).hasSize(corpus.documents().size());
        assertThat(tokenStreams).allSatisfy(tokens -> assertThat(tokens).isNotEmpty());
    }

    @Test
    void flattensEveryTokenStreamInOrderIntoOnePool() {
        List<List<String>> tokenStreams = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e", "f"));

        List<String> pool = BenchmarkCorpus.flattenTokenPool(tokenStreams);

        assertThat(pool).containsExactly("a", "b", "c", "d", "e", "f");
    }
}
