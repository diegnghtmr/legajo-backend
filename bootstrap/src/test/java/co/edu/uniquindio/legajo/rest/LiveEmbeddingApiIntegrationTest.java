package co.edu.uniquindio.legajo.rest;

import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code legajo.embedding-provider=live} against a stubbed OpenAI-compatible embeddings
 * endpoint (feature doc tasks A8/F5, TRD §6.3 "Modo en vivo de {@code embedding-api}
 * (fijado)"): every route that uses {@code embedding-api} fetches live vectors, and a provider
 * failure degrades to 503 with the fixed URN, never a silent fallback to the cached demo. A
 * distinct {@code @SpringBootTest} property set (from every other test class's default {@code
 * cached} context) means Spring caches this as its own context, so its beans — including
 * {@code LiveApiEmbeddingRepository}'s internal vector cache — never share state with the
 * default profile's tests. {@code @DirtiesContext} after every method additionally isolates
 * this class's own test methods from each other, since {@code /clustering} with {@code
 * representation=embedding-api} necessarily touches every corpus document, so no pair of
 * reserved document ids could otherwise keep two methods' caches apart.
 *
 * <p><b>One batched request per test method (F5).</b> {@link
 * co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository#load()} now
 * sends ONE request for every not-yet-cached document instead of one request per document, so
 * every route exercised here — none of which narrow {@code load()} to fewer than the whole
 * corpus — makes exactly one live network round trip per (fresh, thanks to
 * {@code @DirtiesContext}) test method, for all {@link #CORPUS_SIZE} documents at once. The
 * success stubs below build one response covering the whole corpus instead of relying on a
 * WireMock scenario's per-request state transition, which assumed the old one-request-per-
 * document shape.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"legajo.embedding-provider=live", "legajo.embedding-api.dimension=4"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LiveEmbeddingApiIntegrationTest {

    private static WireMockServer wireMockServer;

    /** The real {@code data/corpus.json}'s document count, so every stub below returns exactly
     * as many vectors as the single batched request will ask for — never a fixed guess that
     * silently drifts out of sync with the corpus file. */
    private static final int CORPUS_SIZE =
            new JsonCorpusRepository(Path.of("data/corpus.json")).load().documents().size();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void liveEmbeddingApiProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        registry.add("spring.ai.openai.base-url", wireMockServer::baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @AfterEach
    void stopServer() {
        wireMockServer.stop();
    }

    /** Same vector for every document in the corpus's single batched request. */
    private void stubSuccessForEveryRequest() {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .willReturn(embeddingResponseForWholeCorpus("[1.0, 0.0, 0.0, 0.0]", "[1.0, 0.0, 0.0, 0.0]")));
    }

    /** Two distinct vectors, alternating by position (so {@code d01} at index 0 and {@code
     * d02} at index 1 always differ) inside the ONE response covering the whole corpus, so a
     * clustering distance matrix built from these vectors is never degenerate (all-identical,
     * which would make cophenetic correlation's Pearson denominator zero/NaN). */
    private void stubTwoDistinctVectorsAcrossCalls() {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .willReturn(embeddingResponseForWholeCorpus("[1.0, 0.0, 0.0, 0.0]", "[0.0, 1.0, 0.0, 0.0]")));
    }

    /**
     * Builds a single {@code data} array with exactly {@link #CORPUS_SIZE} items (F5:
     * {@link co.edu.uniquindio.legajo.infrastructure.embedding.LiveApiEmbeddingRepository#load()}
     * now sends one batched request for the whole not-yet-cached corpus and fails closed on a
     * vector-count mismatch, so a stub with fewer items than the corpus would make every test
     * below throw {@code EmbeddingApiException} instead of exercising the intended path).
     * {@code vectorForEvenIndex}/{@code vectorForOddIndex} alternate by position.
     */
    private static ResponseDefinitionBuilder embeddingResponseForWholeCorpus(String vectorForEvenIndex,
            String vectorForOddIndex) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < CORPUS_SIZE; i++) {
            if (i > 0) {
                items.append(',');
            }
            String vector = i % 2 == 0 ? vectorForEvenIndex : vectorForOddIndex;
            items.append("""
                    { "object": "embedding", "index": %d, "embedding": %s }
                    """.formatted(i, vector));
        }
        String body = """
                {
                  "object": "list",
                  "data": [ %s ],
                  "model": "gemini-embedding-2-preview",
                  "usage": { "prompt_tokens": %d, "total_tokens": %d }
                }
                """.formatted(items, CORPUS_SIZE * 3, CORPUS_SIZE * 3);
        return WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @Test
    void compareWithEmbeddingApiSucceedsUsingLiveVectors() throws Exception {
        stubTwoDistinctVectorsAcrossCalls();

        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["embedding-api"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].algorithmId").value("embedding-api"))
                .andExpect(jsonPath("$[0].result.normalizedScore").exists());
    }

    @Test
    void traceWithEmbeddingApiSucceedsUsingLiveVectors() throws Exception {
        stubTwoDistinctVectorsAcrossCalls();

        mockMvc.perform(get("/api/v1/similarity/embedding-api/trace?documentIdA=d01&documentIdB=d02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedScore").exists())
                .andExpect(jsonPath("$.dimension").value(4))
                .andExpect(jsonPath("$.providerStatus").value("live"));
    }

    @Test
    void clusteringWithEmbeddingApiRepresentationWorksUsingLiveVectors() throws Exception {
        stubTwoDistinctVectorsAcrossCalls();

        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"embedding-api","linkages":["single"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linkageId").value("single"))
                .andExpect(jsonPath("$[0].rows").isArray())
                .andExpect(jsonPath("$[0].leafOrder").isArray());
    }

    @Test
    void embeddingsStatusReportsLiveModeTruthfully() throws Exception {
        stubSuccessForEveryRequest();

        mockMvc.perform(get("/api/v1/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingApi.mode").value("live"));
    }

    /** NFR-QA-12: a provider failure on any embedding-api path is 503, never a 500, and
     * never a silent fallback to the versioned cache. */
    @Test
    void compareWithEmbeddingApiAnswers503WhenTheLiveProviderFails() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .willReturn(WireMock.aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"boom\"}}")));

        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["embedding-api"]}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:embedding-api-unavailable"));
    }

    @Test
    void clusteringWithEmbeddingApiAnswers503WhenTheLiveProviderFails() throws Exception {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .willReturn(WireMock.aResponse().withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"message\":\"boom\"}}")));

        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"embedding-api","linkages":["single"]}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:embedding-api-unavailable"));
    }
}
