package co.edu.uniquindio.legajo.infrastructure.embedding;

/**
 * Unchecked failure raised by {@link OpenAiCompatibleEmbedder} when the remote
 * OpenAI-compatible embeddings endpoint cannot be used for a document: a 5xx response, a
 * request timeout, an authentication/authorization failure, or a response whose vector shape
 * does not match the configured dimension (TRD §8's "Modo de fallo" for this integration).
 * The precompute CLI ({@link co.edu.uniquindio.legajo}) is expected to let this fail the
 * batch job closed rather than writing a partial or fabricated cache.
 */
public final class EmbeddingApiException extends RuntimeException {

    public EmbeddingApiException(String message) {
        super(message);
    }

    public EmbeddingApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
