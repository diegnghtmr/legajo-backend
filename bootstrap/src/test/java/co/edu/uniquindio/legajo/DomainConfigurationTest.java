package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the project's first {@code @Configuration} (feature doc, A1) actually registers
 * the beans it promises: {@link CorpusRepository}/{@link EmbeddingRepository} adapters that
 * today are only ever {@code new}-ed by hand in the CLIs, and the six {@code
 * SimilarityAlgorithm} implementations assembled into a {@link SimilarityAlgorithmRegistry}
 * via {@code SimilarityAlgorithmRegistry}'s own documented list-injection pattern.
 *
 * <p>Boots the real Spring context (no web layer needed for this assertion) against the
 * real, versioned {@code data/corpus.json}/{@code data/embeddings-*.json} — the same
 * "cached demo, no network" data every other cached-mode path in this codebase already
 * trusts (TAC-13).
 */
@SpringBootTest
class DomainConfigurationTest {

    @Autowired
    private CorpusRepository corpusRepository;

    @Autowired
    @Qualifier("localEmbeddingRepository")
    private EmbeddingRepository localEmbeddingRepository;

    @Autowired
    @Qualifier("apiEmbeddingRepository")
    private EmbeddingRepository apiEmbeddingRepository;

    @Autowired
    private SimilarityAlgorithmRegistry registry;

    @Test
    void registersTheJsonCorpusRepositoryAdapter() {
        assertThat(corpusRepository).isInstanceOf(JsonCorpusRepository.class);
    }

    @Test
    void registersTwoDistinctJsonEmbeddingRepositoryAdaptersForTheTwoCacheFamilies() {
        assertThat(localEmbeddingRepository).isInstanceOf(JsonEmbeddingRepository.class);
        assertThat(apiEmbeddingRepository).isInstanceOf(JsonEmbeddingRepository.class);
        assertThat(localEmbeddingRepository).isNotSameAs(apiEmbeddingRepository);
    }

    @Test
    void registersExactlyTheSixFixedSimilarityAlgorithms() {
        assertThat(registry.all()).extracting(a -> a.id()).containsExactlyInAnyOrder(
                "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");
    }
}
