package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.corpus.CorpusService;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingsService;
import co.edu.uniquindio.legajo.application.similarity.SimilarityService;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the four A2 application services ({@code CorpusService}, {@code
 * SimilarityService}, {@code ClusteringService}, {@code EmbeddingsService} — the names
 * {@code backend/AGENTS.md}'s application map already lists, plus {@code EmbeddingsService}
 * for {@code GET /embeddings/status}, not previously in that map) as Spring beans, so the
 * REST controllers of A3/A4 can simply {@code @Autowired} them. Every service stays
 * framework-free; only this configuration class (in {@code bootstrap}) knows about Spring.
 *
 * <p>{@code @Qualifier} disambiguates the two same-typed {@link EmbeddingRepository} beans
 * {@link DomainConfiguration} registers ({@code localEmbeddingRepository}/{@code
 * apiEmbeddingRepository}) — {@code SimilarityService} and {@code ClusteringService} both
 * need both caches, one per embedding-based capability/representation.
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
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository) {
        return new SimilarityService(corpusRepository, registry, localEmbeddingRepository, apiEmbeddingRepository);
    }

    @Bean
    public ClusteringService clusteringService(CorpusRepository corpusRepository,
            @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository) {
        return new ClusteringService(corpusRepository, localEmbeddingRepository, apiEmbeddingRepository);
    }

    @Bean
    public EmbeddingsService embeddingsService(CorpusRepository corpusRepository) {
        return new EmbeddingsService(corpusRepository);
    }

    /**
     * Exposes {@code legajo.embedding-provider}'s already-bound, typed value (A1's
     * {@link LegajoProperties}) as its own bean, so {@code EmbeddingsController} (A4) can
     * receive it by constructor injection without depending on {@link LegajoProperties}
     * itself — {@code infrastructure} may not depend on {@code bootstrap} (ArchUnit,
     * TRD §4.3), and this is the one value {@code EmbeddingsController} needs from it.
     */
    @Bean
    public EmbeddingProviderMode embeddingProviderMode(LegajoProperties legajoProperties) {
        return legajoProperties.embeddingProvider();
    }
}
