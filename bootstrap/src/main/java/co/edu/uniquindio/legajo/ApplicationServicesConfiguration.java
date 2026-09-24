package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import co.edu.uniquindio.legajo.application.clustering.ClusteringCacheKey;
import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;
import co.edu.uniquindio.legajo.application.corpus.CorpusService;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingsService;
import co.edu.uniquindio.legajo.application.similarity.SimilarityCacheKey;
import co.edu.uniquindio.legajo.application.similarity.SimilarityService;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the application services ({@code CorpusService}, {@code
 * SimilarityService}, {@code ClusteringService}, {@code EmbeddingsService} for
 * {@code GET /embeddings/status}) as Spring beans, so the
 * REST controllers can simply {@code @Autowired} them. Every service stays
 * framework-free; only this configuration class (in {@code bootstrap}) knows about Spring.
 *
 * <p>{@code @Qualifier} disambiguates the two same-typed {@link EmbeddingRepository} beans
 * {@link DomainConfiguration} registers ({@code localEmbeddingRepository}/{@code
 * apiEmbeddingRepository}) — {@code SimilarityService} and {@code ClusteringService} both
 * need both caches, one per embedding-based capability/representation.
 *
 * <p>{@code similarityCache}/{@code clusteringCache} are disambiguated by their
 * full generic type instead — {@link CacheConfiguration} declares each {@code @Bean}
 * factory method with its own {@code RequestCache<K, V>} return type, and Spring resolves
 * these constructor parameters against that declared generic type, not just the erased
 * {@code RequestCache} type both beans share at runtime.
 */
@Configuration
public class ApplicationServicesConfiguration {

    @Bean
    public CorpusService corpusService(CorpusRepository corpusRepository) {
        return new CorpusService(corpusRepository);
    }

    @Bean
    public SimilarityService similarityService(CorpusRepository corpusRepository,
            SimilarityAlgorithmRegistry registry,
            @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository,
            RequestCache<SimilarityCacheKey, SimilarityResult> similarityCache) {
        return new SimilarityService(
                corpusRepository, registry, localEmbeddingRepository, apiEmbeddingRepository, similarityCache);
    }

    @Bean
    public ClusteringService clusteringService(CorpusRepository corpusRepository,
            @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository,
            RequestCache<ClusteringCacheKey, LinkageRunResult> clusteringCache) {
        return new ClusteringService(
                corpusRepository, localEmbeddingRepository, apiEmbeddingRepository, clusteringCache);
    }

    @Bean
    public EmbeddingsService embeddingsService(CorpusRepository corpusRepository) {
        return new EmbeddingsService(corpusRepository);
    }

    /**
     * Exposes {@code legajo.embedding-provider}'s already-bound, typed value
     * ({@link LegajoProperties}) as its own bean, so {@code EmbeddingsController} can
     * receive it by constructor injection without depending on {@link LegajoProperties}
     * itself — {@code infrastructure} may not depend on {@code bootstrap} (enforced by
     * ArchUnit as one of this codebase's hexagonal dependency rules), and this is the one
     * value {@code EmbeddingsController} needs from it.
     */
    @Bean
    public EmbeddingProviderMode embeddingProviderMode(LegajoProperties legajoProperties) {
        return legajoProperties.embeddingProvider();
    }
}
