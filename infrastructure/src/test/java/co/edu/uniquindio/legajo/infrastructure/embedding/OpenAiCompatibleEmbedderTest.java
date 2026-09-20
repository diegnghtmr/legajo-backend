package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link OpenAiCompatibleEmbedder} against a stubbed OpenAI-compatible embeddings endpoint
 * (TRD §13, "Adaptador remoto"): success (including Gemini's real, index/usage-omitting
 * response shape — see {@link GeminiEmbeddingsCompatibilityInterceptor}), a 5xx response, a
 * 401, a client-side timeout, and a dimension mismatch, all mapped through
 * {@link EmbeddingApiException} rather than reaching the caller as raw SDK exceptions. No test
 * in this class talks to the real network — Gemini's endpoint is exercised separately by the
 * offline precompute run (task S6b), never by {@code ./gradlew test}.
 */
class OpenAiCompatibleEmbedderTest {

    private static final String MODEL = "gemini-embedding-2-preview";
    private static final int DIMENSION = 4;

    private WireMockServer wireMockServer;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
    }

    @AfterEach
    void stopServer() {
        wireMockServer.stop();
    }

    @Test
    void embedsSuccessfullyAndL2NormalizesTheReturnedVector() {
        // Deliberately not already unit length, so a passing test proves this class performs
        // its own L2 normalization (TRD §6.3, "Invariante de norma unitaria (fijado)")
        // instead of trusting the provider's output unchanged.
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "index": 0, "embedding": [1.0, 1.0, 1.0, 1.0] } ],
                  "model": "%s",
                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                }
                """.formatted(MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            EmbeddingVector vector = embedder.embed("d01", "an abstract");

            assertThat(vector.documentId()).isEqualTo("d01");
            assertThat(vector.provider()).isEqualTo("api");
            assertThat(vector.model()).isEqualTo(MODEL);
            assertThat(vector.dimension()).isEqualTo(DIMENSION);
            assertThat(vector.preNormL2()).isCloseTo(2.0, within(1e-9)); // ||(1,1,1,1)|| = 2
            assertThat(EmbeddingVector.l2Norm(vector.values())).isCloseTo(1.0, within(1e-9));
            assertThat(vector.values()).containsExactly(0.5, 0.5, 0.5, 0.5);
        }
    }

    @Test
    void embedsSuccessfullyAgainstGeminisRealResponseShapeWhereIndexIsOmittedForTheFirstItem() {
        // Reproduces the exact shape observed against the real Gemini endpoint (verified with
        // a raw curl call, TRD §8): data[0] has no "index" key at all — Gemini's OpenAI-compat
        // layer omits int32 fields left at their default value (proto3 JSON semantics), and
        // index 0 is that default. Without GeminiEmbeddingsCompatibilityInterceptor, the OpenAI Java
        // SDK's Embedding.index() throws OpenAIInvalidDataException("index is not set")
        // before this method ever returns.
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "embedding": [1.0, 1.0, 1.0, 1.0] } ],
                  "model": "%s"
                }
                """.formatted(MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            EmbeddingVector vector = embedder.embed("d01", "an abstract");

            assertThat(vector.dimension()).isEqualTo(DIMENSION);
            assertThat(EmbeddingVector.l2Norm(vector.values())).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void wrapsAServerErrorResponseInEmbeddingApiException() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"boom\"}}")));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            assertThatThrownBy(() -> embedder.embed("d01", "an abstract"))
                    .isInstanceOf(EmbeddingApiException.class)
                    .hasMessageContaining("d01")
                    .hasMessageContaining(MODEL);
        }
    }

    @Test
    void wrapsAnAuthenticationFailureInEmbeddingApiException() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"invalid api key\"}}")));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("bad-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            assertThatThrownBy(() -> embedder.embed("d01", "an abstract"))
                    .isInstanceOf(EmbeddingApiException.class)
                    .hasMessageContaining("d01");
        }
    }

    @Test
    void wrapsAClientSideTimeoutInEmbeddingApiException() {
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(3000)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        try (OpenAiCompatibleEmbedder embedder = new OpenAiCompatibleEmbedder(
                "test-key", wireMockServer.baseUrl(), MODEL, DIMENSION, Duration.ofMillis(300))) {
            assertThatThrownBy(() -> embedder.embed("d01", "an abstract"))
                    .isInstanceOf(EmbeddingApiException.class)
                    .hasMessageContaining("d01");
        }
    }

    @Test
    void wrapsADimensionMismatchInEmbeddingApiException() {
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "index": 0, "embedding": [1.0, 1.0] } ],
                  "model": "%s",
                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                }
                """.formatted(MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            assertThatThrownBy(() -> embedder.embed("d01", "an abstract"))
                    .isInstanceOf(EmbeddingApiException.class)
                    .hasMessageContaining("d01");
        }
    }
}
