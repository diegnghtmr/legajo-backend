package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.MemoizingEmbeddingRepository;
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
 * Proves the project's first {@code @Configuration} actually registers
 * the beans it promises: {@link CorpusRepository}/{@link EmbeddingRepository} adapters that
 * today are only ever {@code new}-ed by hand in the CLIs, and the six {@code
 * SimilarityAlgorithm} implementations assembled into a {@link SimilarityAlgorithmRegistry}
 * via {@code SimilarityAlgorithmRegistry}'s own documented list-injection pattern.
 *
 * <p>Boots the real Spring context (no web layer needed for this assertion) against the
 * real, versioned {@code data/corpus.json}/{@code data/embeddings-*.json} — the same
 * "cached demo, no network" data every other cached-mode path in this codebase already
 * trusts.
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

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /**
     * The real application context must register the startup validator. The
     * validator's own tests build a minimal context, so without this check removing the
     * {@code @Bean} would leave them green while the server went back to failing lazily.
     */
    @Test
    void registersTheEmbeddingCacheStartupValidator() {
        assertThat(applicationContext.getBean("embeddingCacheStartupValidator"))
                .isInstanceOf(org.springframework.beans.factory.SmartInitializingSingleton.class);
    }

    @Test
    void registersTheJsonCorpusRepositoryAdapter() {
        assertThat(corpusRepository).isInstanceOf(JsonCorpusRepository.class);
    }

    /**
     * Both cache-backed beans are wrapped in {@link MemoizingEmbeddingRepository}: the startup
     * validator's {@code load()} call and every
     * later request-time call must share the same already-validated, already-parsed cache
     * instead of each re-reading {@code JsonEmbeddingRepository}'s underlying file.
     */
    @Test
    void registersTwoDistinctMemoizingEmbeddingRepositoryAdaptersForTheTwoCacheFamilies() {
        assertThat(localEmbeddingRepository).isInstanceOf(MemoizingEmbeddingRepository.class);
        assertThat(apiEmbeddingRepository).isInstanceOf(MemoizingEmbeddingRepository.class);
        assertThat(localEmbeddingRepository).isNotSameAs(apiEmbeddingRepository);
    }

    @Test
    void registersExactlyTheSixFixedSimilarityAlgorithms() {
        assertThat(registry.all()).extracting(a -> a.id()).containsExactlyInAnyOrder(
                "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");
    }

    /**
     * The six capabilities have a fixed
     * order (levenshtein, needleman-wunsch, jaccard, tfidf-cosine, embedding-local,
     * embedding-api), and {@code SimilarityService.catalogue()}/{@code compare()}'s default
     * both rely on {@code registry.all()} preserving it. The test above only proves the set
     * is right (order-insensitive); this proves the real, Spring-wired registry — assembled
     * by ordered list injection over {@code DomainConfiguration}'s six {@code @Bean} methods,
     * not a test-built one — preserves the declaration order those methods are written in.
     */
    @Test
    void registryPreservesTheDeclaredAlgorithmOrder() {
        assertThat(registry.all()).extracting(a -> a.id()).containsExactly(
                "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");
    }

    /**
     * {@code legajo.embedding-provider=live} must switch the
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
        void localEmbeddingRepositoryStaysTheMemoizedJsonAdapterRegardlessOfApiMode() {
            // embedding-local has no live mode: its inference only ever happens in
            // the offline precompute job, so it is still the memoized JsonEmbeddingRepository
            // adapter, never LiveApiEmbeddingRepository.
            assertThat(localEmbeddingRepository).isInstanceOf(MemoizingEmbeddingRepository.class);
        }
    }
}
