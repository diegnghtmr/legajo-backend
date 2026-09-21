package co.edu.uniquindio.legajo.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code legajo.embedding-provider=live} against a stubbed OpenAI-compatible embeddings
 * endpoint (feature doc task A8, TRD §6.3 "Modo en vivo de {@code embedding-api} (fijado)"):
 * every route that uses {@code embedding-api} fetches live vectors, and a provider failure
 * degrades to 503 with the fixed URN, never a silent fallback to the cached demo. A distinct
 * {@code @SpringBootTest} property set (from every other test class's default {@code cached}
 * context) means Spring caches this as its own context, so its beans — including {@code
 * LiveApiEmbeddingRepository}'s internal vector cache — never share state with the default
 * profile's tests. {@code @DirtiesContext} after every method additionally isolates this
 * class's own test methods from each other, since {@code /clustering} with {@code
 * representation=embedding-api} necessarily touches every corpus document, so no pair of
 * reserved document ids could otherwise keep two methods' caches apart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"legajo.embedding-provider=live", "legajo.embedding-api.dimension=4"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LiveEmbeddingApiIntegrationTest {

    private static WireMockServer wireMockServer;

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

    private void stubSuccessForEveryRequest() {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings")).willReturn(embeddingResponse("[1.0, 0.0, 0.0, 0.0]")));
    }

    /** Two distinct vectors — the first call, then every later call — so a clustering
     * distance matrix built from these vectors is never degenerate (all-identical, which
     * would make cophenetic correlation's Pearson denominator zero/NaN). */
    private void stubTwoDistinctVectorsAcrossCalls() {
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .inScenario("live-embeddings")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(embeddingResponse("[1.0, 0.0, 0.0, 0.0]"))
                .willSetStateTo("subsequent"));
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo("/embeddings"))
                .inScenario("live-embeddings")
                .whenScenarioStateIs("subsequent")
                .willReturn(embeddingResponse("[0.0, 1.0, 0.0, 0.0]")));
    }

    private static ResponseDefinitionBuilder embeddingResponse(String vector) {
        String body = """
                {
                  "object": "list",
                  "data": [ { "object": "embedding", "index": 0, "embedding": %s } ],
                  "model": "gemini-embedding-2-preview",
                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                }
                """.formatted(vector);
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
