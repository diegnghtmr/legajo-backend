package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.OpenAiCompatibleEmbedder;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entry point for {@code ./gradlew :bootstrap:precomputeApiEmbeddings [--args="..."]}
 * (TRD §6.1, §6.3, §8, §9, ADR-015): calls {@code gemini-embedding-2-preview} through its
 * OpenAI-compatible layer over every document's abstract in {@code corpus.json} and writes
 * {@code embeddings-openai.json}, binding it to the loaded corpus's {@code corpusSha256} so a
 * stale cache fails closed at load time ({@link JsonEmbeddingRepository}) — mirroring
 * {@link PrecomputeMiniLmEmbeddingsCli}'s shape for the local provider.
 *
 * <p><b>Live network call, unlike the MiniLM CLI.</b> This CLI needs five environment
 * variables set with real values (TRD §14.1's "Variables de entorno del modo en vivo de
 * embedding-api (fijadas)"): {@code SPRING_AI_OPENAI_API_KEY}, {@code
 * SPRING_AI_OPENAI_BASE_URL}, {@code SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH}, {@code
 * LEGAJO_EMBEDDING_API_MODEL}, {@code LEGAJO_EMBEDDING_API_DIMENSION}. It fails fast, before
 * calling the network, if any is missing — never fabricating a cache from a partial or failed
 * run. The API key is read from the environment only and never logged, printed, or included
 * in any exception message this class raises.
 *
 * <p><b>{@code SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH} (documented gap).</b> Spring AI
 * 1.x's {@code OpenAiEmbeddingProperties} had an {@code embeddingsPath} override point; Spring
 * AI 2.0.x rebuilt its OpenAI integration on the official OpenAI Java SDK
 * ({@code com.openai.client.OpenAIClient}) and dropped it — the reference configuration table
 * for {@code spring.ai.openai.embedding.*} in 2.0.x has no {@code embeddings-path} property,
 * and {@link org.springframework.ai.openai.OpenAiEmbeddingOptions.Builder} exposes no such
 * setter (verified against the Spring AI 2.0.0 reference docs). The SDK always POSTs to
 * {@code {base-url}/embeddings}, which is exactly Gemini's OpenAI-compatible layout (TRD §8:
 * {@code https://generativelanguage.googleapis.com/v1beta/openai} + {@code /embeddings}), so
 * this integration needs no override in practice; this class still reads the variable and
 * only warns if it is set to something other than {@code /embeddings}, since Spring AI 2.0.x
 * has nowhere to route it. Worth confirming with the author whether the TRD should drop this
 * variable or note the version-specific gap, the same way S5 flagged its own TRD ambiguity.
 */
public final class PrecomputeApiEmbeddingsCli {

    private static final String EXPECTED_EMBEDDINGS_PATH = "/embeddings";

    private PrecomputeApiEmbeddingsCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String corpusPath = options.getOrDefault("corpus", "data/corpus.json");
        String outputPath = options.getOrDefault("output", "data/embeddings-openai.json");

        String apiKey = requireEnv("SPRING_AI_OPENAI_API_KEY");
        String baseUrl = requireEnv("SPRING_AI_OPENAI_BASE_URL");
        String model = requireEnv("LEGAJO_EMBEDDING_API_MODEL");
        String dimensionRaw = requireEnv("LEGAJO_EMBEDDING_API_DIMENSION");
        String embeddingsPath = System.getenv("SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH");

        int dimension;
        try {
            dimension = Integer.parseInt(dimensionRaw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "LEGAJO_EMBEDDING_API_DIMENSION must be an integer, was '" + dimensionRaw + "'", e);
        }
        if (dimension <= 0) {
            throw new IllegalStateException("LEGAJO_EMBEDDING_API_DIMENSION must be positive, was " + dimension);
        }

        if (embeddingsPath != null && !embeddingsPath.isBlank() && !EXPECTED_EMBEDDINGS_PATH.equals(embeddingsPath)) {
            System.out.printf(
                    "precompute-api-embeddings: WARNING SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH=%s is set, "
                            + "but Spring AI 2.0.x's OpenAI client has no embeddings-path override point (it always "
                            + "requests %s relative to the base URL); this run ignores the variable's value.%n",
                    embeddingsPath, EXPECTED_EMBEDDINGS_PATH);
        }

        Corpus corpus = new JsonCorpusRepository(Path.of(corpusPath)).load();
        System.out.printf(
                "precompute-api-embeddings: %d document(s) loaded from %s (corpusSha256=%s); model=%s dimension=%d "
                        + "baseUrl=%s%n",
                corpus.documents().size(), corpusPath, corpus.corpusSha256(), model, dimension, baseUrl);

        List<EmbeddingVector> vectors = new ArrayList<>(corpus.documents().size());
        try (OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(apiKey, baseUrl, model, dimension)) {
            for (CorpusDocument document : corpus.documents()) {
                EmbeddingVector vector = embedder.embed(document.id(), document.abstractText());
                vectors.add(vector);
                System.out.printf("  %s: dimension=%d preNormL2=%.6f%n", document.id(), vector.dimension(),
                        vector.preNormL2());
            }
        }

        EmbeddingCache cache = new EmbeddingCache("1.0", corpus.version(), corpus.corpusSha256(), model, dimension,
                vectors);
        new JsonEmbeddingRepository(Path.of(outputPath), OpenAiCompatibleEmbedder.PROVIDER, corpus.corpusSha256())
                .save(cache);

        System.out.printf("precompute-api-embeddings: wrote %d vector(s) (dimension=%d) to %s%n", vectors.size(),
                dimension, outputPath);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + name + " is required to run the embedding-api precompute "
                            + "(TRD §14.1); it is missing or blank. Never fabricate embeddings-openai.json without it.");
        }
        return value;
    }
}
