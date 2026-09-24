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
 * job: {@code gemini-embedding-2-preview} through Gemini's OpenAI-compatible layer, by
 * default, but this class only knows the generic {@code base-url}/{@code api-key}/{@code
 * model}/{@code dimensions} shape Spring AI's OpenAI client speaks, not the specific
 * provider — the {@code SPRING_AI_OPENAI_*} prefix names the access path this client
 * speaks, not the model's actual provider.
 *
 * <p>Model inference (the provider's own tokenizer and forward pass, run remotely) is fine
 * to run through a library; the L2 normalization applied on write — even though the
 * provider already returns unit vectors truncated by Matryoshka, per this application's
 * fixed embedding capabilities — stays hand-written in {@link EmbeddingVector#normalize},
 * exactly as {@link MiniLmEmbedder} does for the local provider.
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
 * shape (the model is still in preview) — rather than being cached wrong.
 *
 * <p><b>Verified Gemini/openai-java compatibility gaps.</b> {@link GeminiEmbeddingsCompatibilityInterceptor}
 * is registered on the underlying HTTP client to work around two real, reproduced
 * incompatibilities: Gemini's OpenAI-compatible endpoint omits the per-embedding {@code index}
 * field whenever it would be {@code 0} (a proto3 JSON default-omission quirk) and omits the
 * top-level {@code usage} object entirely, while the official OpenAI Java SDK treats both as
 * required, throwing before this class's own code ever runs. See that class's Javadoc for the
 * verified request/response evidence.
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
                .httpClientBuilderCustomizer(clientBuilder -> clientBuilder.interceptor(new GeminiEmbeddingsCompatibilityInterceptor()))
                .build();
    }

    /**
     * Embeds {@code rawAbstract} for {@code documentId} over the network, then L2-normalizes
     * the returned vector with hand-written code and records the pre-normalization norm as
     * {@code preNormL2} provenance, exactly like {@link MiniLmEmbedder#embed}.
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

    /**
     * Embeds every {@code rawAbstracts} entry with exactly ONE network request, per the
     * fixed live-mode contract for {@code embedding-api}, instead of one request per document,
     * then L2-normalizes each returned vector exactly like {@link #embed}. {@code documentIds}
     * and {@code rawAbstracts} must be the same length and share positional order: the
     * provider's response is expected to preserve request order (the OpenAI embeddings
     * contract's per-item {@code index}), and this method never trusts that silently — a
     * different vector count than requested fails closed with {@link EmbeddingApiException}
     * rather than guessing which vector belongs to which document.
     *
     * <p>No provider-documented batch-size limit is applied: Gemini's OpenAI-compatible
     * embeddings endpoint documents no per-request item cap relevant to this corpus's size,
     * so this sends one request for however many ids are passed in. A future corpus
     * large enough to need chunking should revisit this once such a limit is documented, not
     * invent one.
     *
     * @throws EmbeddingApiException if the remote call fails, the response's vector count
     *             does not match {@code documentIds.size()}, or any vector's dimension does
     *             not match the configured {@code dimension}
     */
    public List<EmbeddingVector> embedBatch(List<String> documentIds, List<String> rawAbstracts) {
        Objects.requireNonNull(documentIds, "documentIds");
        Objects.requireNonNull(rawAbstracts, "rawAbstracts");
        if (documentIds.size() != rawAbstracts.size()) {
            throw new IllegalArgumentException(
                    "documentIds and rawAbstracts must be the same size, were %d and %d"
                            .formatted(documentIds.size(), rawAbstracts.size()));
        }
        if (documentIds.isEmpty()) {
            return List.of();
        }

        List<float[]> outputs;
        try {
            outputs = embeddingModel.embed(rawAbstracts);
        } catch (RuntimeException e) {
            throw new EmbeddingApiException(
                    "Embedding API batch call failed for %d document(s) (model=%s): %s"
                            .formatted(documentIds.size(), model, e.getMessage()),
                    e);
        }

        if (outputs == null || outputs.size() != documentIds.size()) {
            int actual = outputs == null ? -1 : outputs.size();
            throw new EmbeddingApiException(
                    ("Embedding API returned %d vector(s) for a batch of %d document(s) (model=%s); "
                            + "refusing to guess which vector belongs to which document")
                                    .formatted(actual, documentIds.size(), model));
        }

        List<EmbeddingVector> vectors = new ArrayList<>(documentIds.size());
        for (int i = 0; i < documentIds.size(); i++) {
            String documentId = documentIds.get(i);
            float[] output = outputs.get(i);
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
            vectors.add(EmbeddingVector.normalize(documentId, PROVIDER, model, raw));
        }
        return vectors;
    }

    @Override
    public void close() {
        // Nothing to release: Spring AI 2.0.x's plain-builder OpenAiEmbeddingModel exposes no
        // close()/shutdown() hook for the HTTP client it owns internally, and the precompute
        // CLI is a one-shot short-lived process. AutoCloseable is kept only so this class can
        // be used the same try-with-resources way as MiniLmEmbedder in the precompute CLIs.
    }
}
