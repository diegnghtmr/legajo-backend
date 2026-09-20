package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calls one OpenAI-compatible embeddings endpoint over the network for the offline precompute
 * job (TRD §6.3, §8, ADR-015): {@code gemini-embedding-2-preview} through Gemini's
 * OpenAI-compatible layer, by default, but this class only knows the generic
 * {@code base-url}/{@code api-key}/{@code model}/{@code dimensions} shape Spring AI's OpenAI
 * client speaks, not the specific provider (TRD §8: "el prefijo {@code SPRING_AI_OPENAI_*}
 * nombra la vía de acceso ... no al proveedor del modelo").
 *
 * <p>Model inference (the provider's own tokenizer and forward pass, run remotely) is
 * delegable under R-02 (TRD §3.3); the L2 normalization applied on write — even though the
 * provider already returns unit vectors truncated by Matryoshka (TRD §6.3, "Capacidades de
 * embedding (fijadas)") — stays hand-written in {@link EmbeddingVector#normalize}, exactly as
 * {@link MiniLmEmbedder} does for the local provider.
 *
 * <p><b>Failure mapping.</b> Spring AI's OpenAI client (built on the official OpenAI Java SDK)
 * throws unchecked exceptions for every provider failure shape — a 5xx response, an
 * authentication/authorization rejection, or a client-side timeout/IO error — with no common
 * checked contract to declare. This class does not attempt to distinguish those shapes: any
 * exception raised while calling the endpoint is wrapped uniformly in
 * {@link EmbeddingApiException}, naming the document id and the configured model, so the
 * precompute CLI fails the whole batch closed with a clear cause instead of writing a partial
 * or fabricated cache entry. A successful call whose returned vector does not have the
 * configured dimension is treated the same way — the provider silently changed its output
 * shape (TR-10: the model is in preview) — rather than being cached wrong.
 */
public final class OpenAiCompatibleEmbedder implements AutoCloseable {

    /** The {@code provider} value every {@link EmbeddingVector} built by this class carries. */
    public static final String PROVIDER = "api";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final OpenAiEmbeddingModel embeddingModel;
    private final String model;
    private final int dimension;

    public OpenAiCompatibleEmbedder(String apiKey, String baseUrl, String model, int dimension) {
        this(apiKey, baseUrl, model, dimension, DEFAULT_TIMEOUT);
    }

    public OpenAiCompatibleEmbedder(String apiKey, String baseUrl, String model, int dimension, Duration timeout) {
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(timeout, "timeout");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive, was " + dimension);
        }
        this.model = model;
        this.dimension = dimension;

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .dimensions(dimension)
                .timeout(timeout)
                .build();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .options(options)
                .metadataMode(MetadataMode.EMBED)
                .build();
    }

    /**
     * Embeds {@code rawAbstract} for {@code documentId} over the network, then L2-normalizes
     * the returned vector with hand-written code and records the pre-normalization norm as
     * {@code preNormL2} provenance (TRD §6.3), exactly like {@link MiniLmEmbedder#embed}.
     *
     * @throws EmbeddingApiException if the remote call fails (5xx, timeout, auth) or returns a
     *             vector whose dimension does not match the configured {@code dimension}
     */
    public EmbeddingVector embed(String documentId, String rawAbstract) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(rawAbstract, "rawAbstract");

        float[] output;
        try {
            output = embeddingModel.embed(rawAbstract);
        } catch (RuntimeException e) {
            throw new EmbeddingApiException(
                    "Embedding API call failed for document '%s' (model=%s): %s"
                            .formatted(documentId, model, e.getMessage()),
                    e);
        }

        if (output == null || output.length != dimension) {
            int actual = output == null ? -1 : output.length;
            throw new EmbeddingApiException(
                    "Embedding API returned %d dimension(s) for document '%s', expected %d (model=%s)"
                            .formatted(actual, documentId, dimension, model));
        }

        List<Double> raw = new ArrayList<>(output.length);
        for (float component : output) {
            raw.add((double) component);
        }
        return EmbeddingVector.normalize(documentId, PROVIDER, model, raw);
    }

    @Override
    public void close() {
        // Nothing to release: Spring AI 2.0.x's plain-builder OpenAiEmbeddingModel exposes no
        // close()/shutdown() hook for the HTTP client it owns internally, and the precompute
        // CLI is a one-shot short-lived process. AutoCloseable is kept only so this class can
        // be used the same try-with-resources way as MiniLmEmbedder in the precompute CLIs.
    }
}
