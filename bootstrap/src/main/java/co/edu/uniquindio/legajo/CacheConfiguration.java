package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import co.edu.uniquindio.legajo.application.clustering.ClusteringCacheKey;
import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;
import co.edu.uniquindio.legajo.application.similarity.SimilarityCacheKey;
import co.edu.uniquindio.legajo.infrastructure.cache.CaffeineRequestCache;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the two request-keyed caches TRD §9 calls Caffeine "regions" (task A5): one for
 * {@code SimilarityService} (key: algorithm id + directional document pair) and one for
 * {@code ClusteringService} (key: representation + linkage id). No TTL — the corpus is
 * static — so a plain {@code Caffeine.newBuilder().build()} region ({@code
 * CaffeineRequestCache}) is enough. NFR-QA-01's "cache disabled" benchmark case is served by
 * constructor-injecting {@code NoOpRequestCache} instead of one of these beans, not by a new
 * configuration key (this module does not wire that case; the {@code :benchmarks} harness
 * that eventually needs it is out of this feature's scope, feature doc {@code rest-api.md}).
 *
 * <p>Each service gets its own cache bean, never a single shared one keyed by a common
 * supertype: {@code SimilarityCacheKey} and {@code ClusteringCacheKey} are unrelated types
 * with no shared meaning, so a single cache would either need unsafe casts or a wrapper key
 * type neither service's Javadoc documents.
 */
@Configuration
public class CacheConfiguration {

    @Bean
    public RequestCache<SimilarityCacheKey, SimilarityResult> similarityCache() {
        return new CaffeineRequestCache<>();
    }

    @Bean
    public RequestCache<ClusteringCacheKey, LinkageRunResult> clusteringCache() {
        return new CaffeineRequestCache<>();
    }
}
