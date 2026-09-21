package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

/**
 * The project's first {@code @Configuration} (feature doc, A1): registers the
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
 * in this class's declaration order (TRD §6.3), without a single Spring import reaching
 * {@code domain}.
 *
 * <p><b>Two {@link EmbeddingRepository} beans, not one.</b> The two embedding-based
 * capabilities ({@code embedding-local}, {@code embedding-api}) each read a different cache
 * file ({@code data/embeddings-minilm.json}, {@code data/embeddings-openai.json|}); a single
 * {@code EmbeddingRepository} bean cannot serve both, so this class registers two, qualified
 * by name ({@code localEmbeddingRepository}/{@code apiEmbeddingRepository}) rather than by
 * type, following {@code JsonEmbeddingRepository}'s existing constructor shape (provider
 * label + the corpus's {@code corpusSha256}, TRD §6.1's fail-closed cache-binding rule) the
 * same way {@code PrecomputeMiniLmEmbeddingsCli}/{@code PrecomputeApiEmbeddingsCli} already
 * do by hand.
 *
 * <p>Paths are the same {@code data/corpus.json}/{@code data/embeddings-*.json} defaults the
 * CLIs use, resolved relative to the backend project root — {@code bootstrap/build.gradle.kts}
 * pins both the {@code test} and {@code bootRun} tasks' working directory there for exactly
 * this reason (Gradle's default working directory for a subproject task is that subproject's
 * own directory, not the backend root {@code data/} actually lives in).
 *
 * <p><b>{@code apiEmbeddingRepository}'s two implementations (feature doc task A8, TRD §6.3
 * "Modo en vivo de {@code embedding-api} (fijado)").</b> {@code legajo.embedding-provider}
 * (A1's {@link LegajoProperties}) selects which adapter backs this bean: {@code cached} (the
 * default) keeps the versioned {@link JsonEmbeddingRepository}, unchanged; {@code live}
 * selects {@link LiveApiEmbeddingRepository}, which fetches vectors from the remote model at
 * request time. The four documented env vars ({@code SPRING_AI_OPENAI_API_KEY}/{@code
 * SPRING_AI_OPENAI_BASE_URL}/{@code LEGAJO_EMBEDDING_API_MODEL}/{@code
 * LEGAJO_EMBEDDING_API_DIMENSION}) are read here via Spring's relaxed environment-variable
 * binding, all with safe defaults (blank/1536), so a {@code live}-configured context with a
 * missing key still starts — {@link LiveApiEmbeddingRepository} itself defers building its
 * network client to first use, so a missing key answers 503 on the first request that needs
 * it, never a startup failure (TRD: "no hay reserva silenciosa ... clave ausente responden
 * 503").
 */
@Configuration
public class DomainConfiguration {

    private static final Path CORPUS_PATH = Path.of("data/corpus.json");
    private static final Path LOCAL_EMBEDDINGS_PATH = Path.of("data/embeddings-minilm.json");
    private static final Path API_EMBEDDINGS_PATH = Path.of("data/embeddings-openai.json");

    @Bean
    public CorpusRepository corpusRepository() {
        return new JsonCorpusRepository(CORPUS_PATH);
    }

    @Bean(name = "localEmbeddingRepository")
    public EmbeddingRepository localEmbeddingRepository(CorpusRepository corpusRepository) {
        Corpus corpus = corpusRepository.load();
        return new JsonEmbeddingRepository(LOCAL_EMBEDDINGS_PATH, MiniLmEmbedder.PROVIDER, corpus.corpusSha256());
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
        return new JsonEmbeddingRepository(API_EMBEDDINGS_PATH, OpenAiCompatibleEmbedder.PROVIDER,
                corpus.corpusSha256());
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

    /** The trace's provider status must name the mode actually serving vectors (TRD 1.3.7 §6.3). */
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
