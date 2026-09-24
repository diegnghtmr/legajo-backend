package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link OpenAiCompatibleEmbedder} against a stubbed OpenAI-compatible embeddings endpoint,
 * exercising the remote embedding adapter: success (including Gemini's real, index/usage-omitting
 * response shape — see {@link GeminiEmbeddingsCompatibilityInterceptor}), a 5xx response, a
 * 401, a client-side timeout, and a dimension mismatch, all mapped through
 * {@link EmbeddingApiException} rather than reaching the caller as raw SDK exceptions. No test
 * in this class talks to the real network — Gemini's endpoint is exercised separately by the
 * offline precompute run, never by {@code ./gradlew test}.
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
        // its own L2 normalization, per the fixed unit-norm invariant,
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
        // a raw curl call): data[0] has no "index" key at all — Gemini's OpenAI-compat
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

    /**
     * {@link OpenAiCompatibleEmbedder#embedBatch}
     * must send every abstract in ONE request and map the returned vectors back to
     * document ids strictly by position. Each stub vector below is distinct (a different
     * one-hot direction) precisely so a positional mix-up (e.g. reversing the list, or an
     * off-by-one) makes this assertion fail instead of passing by coincidence.
     */
    @Test
    void embedBatchSendsExactlyOneRequestForAllAbstractsInOrderAndMapsVectorsToTheRightDocumentIds() {
        String body = """
                {
                  "object": "list",
                  "data": [
                    { "object": "embedding", "index": 0, "embedding": [1.0, 0.0, 0.0, 0.0] },
                    { "object": "embedding", "index": 1, "embedding": [0.0, 1.0, 0.0, 0.0] },
                    { "object": "embedding", "index": 2, "embedding": [0.0, 0.0, 1.0, 0.0] }
                  ],
                  "model": "%s",
                  "usage": { "prompt_tokens": 9, "total_tokens": 9 }
                }
                """.formatted(MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            List<EmbeddingVector> vectors = embedder.embedBatch(List.of("d01", "d02", "d03"),
                    List.of("abstract one", "abstract two", "abstract three"));

            assertThat(vectors).hasSize(3);
            assertThat(vectors.get(0).documentId()).isEqualTo("d01");
            assertThat(vectors.get(0).values()).containsExactly(1.0, 0.0, 0.0, 0.0);
            assertThat(vectors.get(1).documentId()).isEqualTo("d02");
            assertThat(vectors.get(1).values()).containsExactly(0.0, 1.0, 0.0, 0.0);
            assertThat(vectors.get(2).documentId()).isEqualTo("d03");
            assertThat(vectors.get(2).values()).containsExactly(0.0, 0.0, 1.0, 0.0);

            wireMockServer.verify(1, postRequestedFor(urlEqualTo("/embeddings")));
            LoggedRequest request = wireMockServer.getAllServeEvents().get(0).getRequest();
            JsonNode requestBody = JsonMapper.builder().build().readTree(request.getBodyAsString());
            JsonNode input = requestBody.get("input");
            assertThat(input.size()).isEqualTo(3);
            assertThat(input.get(0).asString()).isEqualTo("abstract one");
            assertThat(input.get(1).asString()).isEqualTo("abstract two");
            assertThat(input.get(2).asString()).isEqualTo("abstract three");
        }
    }

    @Test
    void embedBatchThrowsWhenTheProviderReturnsFewerVectorsThanRequestedInstead() {
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "index": 0, "embedding": [1.0, 0.0, 0.0, 0.0] } ],
                  "model": "%s",
                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                }
                """.formatted(MODEL);
        wireMockServer.stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));

        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            assertThatThrownBy(() -> embedder.embedBatch(List.of("d01", "d02", "d03"),
                    List.of("abstract one", "abstract two", "abstract three")))
                    .isInstanceOf(EmbeddingApiException.class)
                    .hasMessageContaining("3");
        }
    }

    @Test
    void embedBatchOfEmptyInputsReturnsAnEmptyListWithoutAnyNetworkCall() {
        try (OpenAiCompatibleEmbedder embedder =
                new OpenAiCompatibleEmbedder("test-key", wireMockServer.baseUrl(), MODEL, DIMENSION)) {
            List<EmbeddingVector> vectors = embedder.embedBatch(List.of(), List.of());

            assertThat(vectors).isEmpty();
            wireMockServer.verify(0, postRequestedFor(urlEqualTo("/embeddings")));
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
