package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import co.edu.uniquindio.legajo.application.similarity.SimilarityCacheKey;
import co.edu.uniquindio.legajo.infrastructure.cache.CaffeineRequestCache;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the request-keyed caches TRD §9 calls Caffeine "regions" (task A5): one bean per
 * cached computation, so far {@code SimilarityService} (key: algorithm id + directional
 * document pair). No TTL — the corpus is static — so a plain {@code
 * Caffeine.newBuilder().build()} region ({@code CaffeineRequestCache}) is enough.
 * NFR-QA-01's "cache disabled" benchmark case is served by constructor-injecting {@code
 * NoOpRequestCache} instead of one of these beans, not by a new configuration key (this
 * module does not wire that case; the {@code :benchmarks} harness that eventually needs it
 * is out of this feature's scope, feature doc {@code rest-api.md}).
 */
@Configuration
public class CacheConfiguration {

    @Bean
    public RequestCache<SimilarityCacheKey, SimilarityResult> similarityCache() {
        return new CaffeineRequestCache<>();
    }
}
