package co.edu.uniquindio.legajo.rest;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code legajo.embedding-provider=cached} (the default, demo profile) must never contact the
 * network, even when {@code spring.ai.openai.base-url} happens to be set: cached mode must
 * never reach the network under any configuration. {@code
 * spring.ai.openai.base-url} points at a real {@link WireMockServer} with <b>no stub
 * registered</b>: if any code path wrongly built a live client and called it, the request
 * would either fail to connect or hit WireMock's default unmatched-request 404, either way
 * observable. This class uses a distinct {@code @SpringBootTest} property set, so Spring
 * caches it separately from every other (default-profile, no {@code base-url} override) test
 * context — the same isolation {@link LiveEmbeddingApiIntegrationTest} relies on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "legajo.embedding-provider=cached")
@AutoConfigureMockMvc
class CachedEmbeddingApiIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void unreachableLiveEmbeddingApiProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        // Deliberately no stubFor(...): any request that reaches this server is itself proof
        // of a wiring defect, whatever WireMock answers with.
        registry.add("spring.ai.openai.base-url", wireMockServer::baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
    }

    // Stopped once, after every test: stopping it after the first test would make the next
    // one's "no serve events" assertion pass vacuously, because a request to a stopped server
    // is refused instead of recorded.
    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @Test
    void compareAndClusteringWithEmbeddingApiNeverContactTheNetworkInCachedMode() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["embedding-api"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result.normalizedScore").exists());

        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"embedding-api","linkages":["single"]}
                                """))
                .andExpect(status().isOk());

        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }

    @Test
    void embeddingsStatusReportsCachedModeAndStillNeverContactsTheNetwork() throws Exception {
        mockMvc.perform(get("/api/v1/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingApi.mode").value("cached"));

        assertThat(wireMockServer.getAllServeEvents()).isEmpty();
    }
}
