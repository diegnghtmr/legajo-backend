package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §6.1/§9, TAC-13 ("corpusSha256 discrepante → error de arranque"): {@link
 * EmbeddingCacheStartupValidator} must stop the Spring context from starting when a
 * mismatched embedding cache is one the configured {@link
 * co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode} actually serves.
 *
 * <p>Uses a minimal {@link ApplicationContextRunner}-built context that wires the exact same
 * shape {@code DomainConfiguration} does ({@code localEmbeddingRepository}/{@code
 * apiEmbeddingRepository} beans, disambiguated by {@code @Qualifier}, plus a {@link
 * SmartInitializingSingleton} bean running the validator) against real {@link
 * JsonEmbeddingRepository} instances pointed at {@code @TempDir} files — never the real,
 * versioned {@code data/embeddings-*.json} the production {@code DomainConfiguration} always
 * points at, since its paths are not externalized as properties (see {@code
 * DomainConfiguration}'s hardcoded {@code CORPUS_PATH}/{@code *_EMBEDDINGS_PATH} constants).
 */
class EmbeddingCacheStartupValidatorTest {

    private static final String CORPUS_SHA = "corpus-sha-current";
    private static final String STALE_SHA = "corpus-sha-stale";

    @TempDir
    Path tempDir;

    @Test
    void localCacheMismatchStopsTheBootAndNamesThePrecomputeCommand() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), STALE_SHA);
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), CORPUS_SHA);

        runner(localPath, apiPath, "cached").run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = rootCause(context.getStartupFailure());
            assertThat(rootCause).isInstanceOf(IllegalStateException.class);
            assertThat(rootCause.getMessage())
                    .contains("precomputeEmbeddings")
                    .contains(STALE_SHA)
                    .contains(CORPUS_SHA);
        });
    }

    @Test
    void apiCacheMismatchInCachedModeStopsTheBoot() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), CORPUS_SHA);
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), STALE_SHA);

        runner(localPath, apiPath, "cached").run(context -> {
            assertThat(context).hasFailed();
            Throwable rootCause = rootCause(context.getStartupFailure());
            assertThat(rootCause).isInstanceOf(IllegalStateException.class);
            assertThat(rootCause.getMessage()).contains("precomputeEmbeddings");
        });
    }

    @Test
    void liveModeSkipsTheMismatchedApiCacheAndStillStarts() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), CORPUS_SHA);
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), STALE_SHA);

        runner(localPath, apiPath, "live").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void liveModeStillStopsTheBootOnAMismatchedLocalCache() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), STALE_SHA);
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), STALE_SHA);

        runner(localPath, apiPath, "live").run(context -> {
            assertThat(context).hasFailed();
            assertThat(rootCause(context.getStartupFailure())).isInstanceOf(IllegalStateException.class);
        });
    }

    /**
     * The boundary is wider than a sha mismatch on purpose: a served cache that cannot be read
     * at all is as unusable as a stale one, so it also stops the boot instead of answering 500
     * on the first request.
     */
    @Test
    void aMissingServedCacheFileAlsoStopsTheBoot() {
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), CORPUS_SHA);

        runner(tempDir.resolve("absent-minilm.json"), apiPath, "cached")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void aMissingApiCacheFileDoesNotStopTheBootInLiveMode() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), CORPUS_SHA);

        runner(localPath, tempDir.resolve("absent-openai.json"), "live")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void matchingCachesInCachedModeStartCleanly() {
        Path localPath = writeCache(tempDir.resolve("embeddings-minilm.json"), CORPUS_SHA);
        Path apiPath = writeCache(tempDir.resolve("embeddings-openai.json"), CORPUS_SHA);

        runner(localPath, apiPath, "cached").run(context -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner runner(Path localPath, Path apiPath, String mode) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "legajo.embedding-provider=" + mode,
                        "test.local-embeddings-path=" + localPath,
                        "test.api-embeddings-path=" + apiPath);
    }

    private static Path writeCache(Path path, String storedCorpusSha) {
        JsonEmbeddingRepository writer = new JsonEmbeddingRepository(path, "provider-x", storedCorpusSha);
        writer.save(new EmbeddingCache("1.0", "1.0", storedCorpusSha, "model-x", 2,
                List.of(new EmbeddingVector("d01", "provider-x", "model-x", 1.0, List.of(0.6, 0.8)))));
        return path;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration
    @EnableConfigurationProperties(LegajoProperties.class)
    static class TestConfig {

        @Bean(name = "localEmbeddingRepository")
        EmbeddingRepository localEmbeddingRepository(@Value("${test.local-embeddings-path}") String path) {
            return new JsonEmbeddingRepository(Path.of(path), "local", CORPUS_SHA);
        }

        @Bean(name = "apiEmbeddingRepository")
        EmbeddingRepository apiEmbeddingRepository(@Value("${test.api-embeddings-path}") String path) {
            return new JsonEmbeddingRepository(Path.of(path), "api", CORPUS_SHA);
        }

        @Bean
        SmartInitializingSingleton embeddingCacheStartupValidator(
                @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
                @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository,
                LegajoProperties legajoProperties) {
            // Delegates to the production factory, so these tests exercise DomainConfiguration's
            // own wiring of the validator, not a copy of it.
            return new DomainConfiguration().embeddingCacheStartupValidator(
                    localEmbeddingRepository, apiEmbeddingRepository, legajoProperties);
        }
    }
}
