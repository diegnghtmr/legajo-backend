package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

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

    /**
     * Feature doc task A8, TRD §6.3: {@code legajo.embedding-provider=live} must switch the
     * {@code apiEmbeddingRepository} bean to {@link LiveApiEmbeddingRepository} — and the
     * context must still start with no {@code SPRING_AI_OPENAI_*}/{@code
     * LEGAJO_EMBEDDING_API_*} variables configured at all, proving the live client is never
     * built eagerly (a missing key must degrade per request, never at startup).
     */
    @Nested
    @SpringBootTest
    @TestPropertySource(properties = "legajo.embedding-provider=live")
    class WithLiveModeConfigured {

        @Autowired
        @Qualifier("apiEmbeddingRepository")
        private EmbeddingRepository apiEmbeddingRepository;

        @Autowired
        @Qualifier("localEmbeddingRepository")
        private EmbeddingRepository localEmbeddingRepository;

        @Test
        void apiEmbeddingRepositoryIsTheLiveAdapter() {
            assertThat(apiEmbeddingRepository).isInstanceOf(LiveApiEmbeddingRepository.class);
        }

        @Test
        void localEmbeddingRepositoryStaysTheJsonAdapterRegardlessOfApiMode() {
            // embedding-local has no live mode (TRD §6.3): its inference only ever happens in
            // the offline precompute job.
            assertThat(localEmbeddingRepository).isInstanceOf(JsonEmbeddingRepository.class);
        }
    }
}
