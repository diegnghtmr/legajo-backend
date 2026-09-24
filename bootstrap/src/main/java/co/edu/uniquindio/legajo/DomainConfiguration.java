package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarksService;
import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.infrastructure.benchmarks.CsvBenchmarkReportRepository;
import co.edu.uniquindio.legajo.infrastructure.benchmarks.MemoizingBenchmarkReportRepository;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.MemoizingEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.MiniLmEmbedder;
import co.edu.uniquindio.legajo.infrastructure.embedding.OpenAiCompatibleEmbedder;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingApi;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocal;
import co.edu.uniquindio.legajo.similarity.Jaccard;
import co.edu.uniquindio.legajo.similarity.Levenshtein;
import co.edu.uniquindio.legajo.similarity.NeedlemanWunsch;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

/**
 * This configuration class registers the
 * {@link CorpusRepository}/{@link EmbeddingRepository} adapters that today are only ever
 * {@code new}-ed by hand inside the five offline CLIs, and the six {@link SimilarityAlgorithm}
 * implementations assembled into a {@link SimilarityAlgorithmRegistry}.
 *
 * <p><b>Domain stays framework-free (ArchUnit-enforced).</b> The six algorithm classes below
 * are never annotated themselves (that would import {@code org.springframework.stereotype.Component}
 * into a package ArchUnit's {@code domainHasNoFrameworkDependency} rule forbids from touching
 * Spring at all) — instead, each gets its own {@code @Bean} factory method here, in
 * {@code bootstrap}, so Spring's ordered list injection collects the resulting
 * {@code List<SimilarityAlgorithm>} into {@link SimilarityAlgorithmRegistry}'s constructor,
 * in this class's declaration order, without a single Spring import reaching
 * {@code domain}.
 *
 * <p><b>Two {@link EmbeddingRepository} beans, not one.</b> The two embedding-based
 * capabilities ({@code embedding-local}, {@code embedding-api}) each read a different cache
 * file ({@code data/embeddings-minilm.json}, {@code data/embeddings-openai.json|}); a single
 * {@code EmbeddingRepository} bean cannot serve both, so this class registers two, qualified
 * by name ({@code localEmbeddingRepository}/{@code apiEmbeddingRepository}) rather than by
 * type, following {@code JsonEmbeddingRepository}'s existing constructor shape (provider
 * label + the corpus's {@code corpusSha256}, which binds each cache file to the exact corpus
 * it was computed from and fails closed on a mismatch) the
 * same way {@code PrecomputeMiniLmEmbeddingsCli}/{@code PrecomputeApiEmbeddingsCli} already
 * do by hand.
 *
 * <p>Paths are the same {@code data/corpus.json}/{@code data/embeddings-*.json} defaults the
 * CLIs use, resolved relative to the backend project root — {@code bootstrap/build.gradle.kts}
 * pins both the {@code test} and {@code bootRun} tasks' working directory there for exactly
 * this reason (Gradle's default working directory for a subproject task is that subproject's
 * own directory, not the backend root {@code data/} actually lives in).
 *
 * <p><b>{@code apiEmbeddingRepository}'s two implementations (live mode for {@code
 * embedding-api}).</b> {@code legajo.embedding-provider}
 * ({@link LegajoProperties}) selects which adapter backs this bean: {@code cached} (the
 * default) keeps the versioned {@link JsonEmbeddingRepository}, unchanged; {@code live}
 * selects {@link LiveApiEmbeddingRepository}, which fetches vectors from the remote model at
 * request time. The four documented env vars ({@code SPRING_AI_OPENAI_API_KEY}/{@code
 * SPRING_AI_OPENAI_BASE_URL}/{@code LEGAJO_EMBEDDING_API_MODEL}/{@code
 * LEGAJO_EMBEDDING_API_DIMENSION}) are read here via Spring's relaxed environment-variable
 * binding, all with safe defaults (blank/1536), so a {@code live}-configured context with a
 * missing key still starts — {@link LiveApiEmbeddingRepository} itself defers building its
 * network client to first use, so a missing key answers 503 on the first request that needs
 * it, never a startup failure: a missing key must never fall back silently, so a request made
 * without one answers 503.
 *
 * <p><b>Startup fail-closed and one shared, already-validated cache.</b> {@code embeddingCacheStartupValidator}
 * below loads {@code localEmbeddingRepository} unconditionally, and {@code
 * apiEmbeddingRepository} only in {@code cached} mode, once every singleton bean is built, so a
 * mismatched {@code corpusSha256} stops the boot instead of only surfacing on the first
 * request. Both {@code JsonEmbeddingRepository}-backed beans are wrapped in {@link
 * MemoizingEmbeddingRepository} for exactly this reason: without it, that startup call would
 * just be one more full re-read and re-validation on top of the one every request already
 * pays for (each of {@code SimilarityService}/{@code ClusteringService}/{@code
 * EmbeddingsService} calls {@code EmbeddingRepository#load()} per request); wrapping the bean
 * makes the startup call and every later request-time call share the exact same parsed cache
 * instead. The {@code live}-mode {@link LiveApiEmbeddingRepository} is never wrapped or
 * validated at startup — it is not a file-backed cache, so startup validation does not apply
 * to it.
 */
@Configuration
public class DomainConfiguration {

    private static final Path CORPUS_PATH = Path.of("data/corpus.json");
    private static final Path LOCAL_EMBEDDINGS_PATH = Path.of("data/embeddings-minilm.json");
    private static final Path API_EMBEDDINGS_PATH = Path.of("data/embeddings-openai.json");
    private static final Path BENCHMARK_RESULTS_CSV = Path.of("benchmarks/results/jmh-results.csv");
    private static final Path BENCHMARK_SLOPES_CSV = Path.of("benchmarks/results/slopes.csv");

    @Bean
    public CorpusRepository corpusRepository() {
        return new JsonCorpusRepository(CORPUS_PATH);
    }

    /**
     * Reads the two versioned JMH export CSVs, wrapped in
     * {@link MemoizingBenchmarkReportRepository} for the same reason the two embedding
     * repositories above are — {@link #benchmarksStartupValidator} calls {@code load()} once
     * at boot, and {@code BenchmarksController} calls it again on every request; without the
     * decorator both would re-read and re-parse the CSVs every time, even though they never
     * change while the server runs. Paths are relative to the backend project root, the same
     * convention {@link #CORPUS_PATH} uses (see this class's own Javadoc on
     * {@code bootstrap/build.gradle.kts} pinning the working directory there).
     */
    @Bean
    public BenchmarkReportRepository benchmarkReportRepository() {
        return new MemoizingBenchmarkReportRepository(
                new CsvBenchmarkReportRepository(BENCHMARK_RESULTS_CSV, BENCHMARK_SLOPES_CSV));
    }

    @Bean
    public BenchmarksService benchmarksService(BenchmarkReportRepository benchmarkReportRepository) {
        return new BenchmarksService(benchmarkReportRepository);
    }

    /**
     * If the benchmark CSVs are missing or malformed, the server must fail to start rather
     * than only fail on the first request: registered as a {@link SmartInitializingSingleton}, the
     * same hook {@link #embeddingCacheStartupValidator} uses, so a missing or malformed
     * benchmark CSV export stops {@code refresh()} instead of only surfacing on the first
     * {@code GET /api/v1/benchmarks} request.
     */
    @Bean
    public SmartInitializingSingleton benchmarksStartupValidator(BenchmarkReportRepository benchmarkReportRepository) {
        BenchmarksStartupValidator validator = new BenchmarksStartupValidator(benchmarkReportRepository);
        return validator::validate;
    }

    @Bean(name = "localEmbeddingRepository")
    public EmbeddingRepository localEmbeddingRepository(CorpusRepository corpusRepository) {
        Corpus corpus = corpusRepository.load();
        return new MemoizingEmbeddingRepository(
                new JsonEmbeddingRepository(LOCAL_EMBEDDINGS_PATH, MiniLmEmbedder.PROVIDER, corpus.corpusSha256()));
    }

    @Bean(name = "apiEmbeddingRepository")
    public EmbeddingRepository apiEmbeddingRepository(CorpusRepository corpusRepository,
            LegajoProperties legajoProperties,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:}") String baseUrl,
            @Value("${legajo.embedding-api.model:gemini-embedding-2-preview}") String apiModel,
            @Value("${legajo.embedding-api.dimension:1536}") int apiDimension) {
        if (legajoProperties.embeddingProvider() == EmbeddingProviderMode.LIVE) {
            return new LiveApiEmbeddingRepository(corpusRepository, apiKey, baseUrl, apiModel, apiDimension);
        }
        Corpus corpus = corpusRepository.load();
        return new MemoizingEmbeddingRepository(new JsonEmbeddingRepository(API_EMBEDDINGS_PATH,
                OpenAiCompatibleEmbedder.PROVIDER, corpus.corpusSha256()));
    }

    /**
     * Fails application startup, not just the first request, when a
     * cache the configured mode actually serves was precomputed for a different corpus.
     * Registered as a {@link
     * SmartInitializingSingleton} — Spring's standard hook for "run this once every singleton
     * bean, including both {@code EmbeddingRepository} beans above, is fully constructed" —
     * so {@link EmbeddingCacheStartupValidator#validate()}'s {@link IllegalStateException} (on
     * a {@code corpusSha256} mismatch, naming the {@code precomputeEmbeddings} task) still
     * aborts {@code refresh()} instead of only surfacing on the first request that needs the
     * mismatched cache.
     */
    @Bean
    public SmartInitializingSingleton embeddingCacheStartupValidator(
            @Qualifier("localEmbeddingRepository") EmbeddingRepository localEmbeddingRepository,
            @Qualifier("apiEmbeddingRepository") EmbeddingRepository apiEmbeddingRepository,
            LegajoProperties legajoProperties) {
        EmbeddingCacheStartupValidator validator = new EmbeddingCacheStartupValidator(
                localEmbeddingRepository, apiEmbeddingRepository, legajoProperties.embeddingProvider());
        return validator::validate;
    }

    @Bean
    public Levenshtein levenshtein() {
        return new Levenshtein();
    }

    @Bean
    public NeedlemanWunsch needlemanWunsch() {
        return new NeedlemanWunsch();
    }

    @Bean
    public Jaccard jaccard() {
        return new Jaccard();
    }

    @Bean
    public TfIdfCosine tfIdfCosine() {
        return new TfIdfCosine();
    }

    @Bean
    public EmbeddingLocal embeddingLocal() {
        return new EmbeddingLocal();
    }

    /** The trace's provider status must name the mode actually serving vectors. */
    @Bean
    public EmbeddingApi embeddingApi(LegajoProperties legajoProperties) {
        return new EmbeddingApi(legajoProperties.embeddingProvider().id());
    }

    /**
     * Spring's ordered list injection collects every {@code SimilarityAlgorithm}-typed
     * {@code @Bean} declared above, in declaration order, exactly the shape
     * {@link SimilarityAlgorithmRegistry}'s constructor documents.
     */
    @Bean
    public SimilarityAlgorithmRegistry similarityAlgorithmRegistry(List<SimilarityAlgorithm> algorithms) {
        return new SimilarityAlgorithmRegistry(algorithms);
    }
}
