package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * TRD §14.4 (1.3.8): a defined {@code LEGAJO_CORS_ORIGINS} list <b>replaces</b> the local
 * development defaults, it does not extend them — in Render this is set to the Vercel
 * origin. A separate {@code @SpringBootTest} property set from
 * {@link CorsDefaultOriginsIntegrationTest} so Spring caches the two contexts independently
 * (same isolation reasoning as {@code CachedEmbeddingApiIntegrationTest}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "legajo.cors-origins=https://legajo.vercel.app")
@AutoConfigureMockMvc
class CorsExplicitOriginsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final URI CORPUS_ENDPOINT = URI.create("http://legajo-api.test/api/v1/corpus");

    @Test
    void theExplicitlyConfiguredOriginIsAllowed() throws Exception {
        mockMvc.perform(options(CORPUS_ENDPOINT)
                        .header("Origin", "https://legajo.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://legajo.vercel.app"));
    }

    @Test
    void theExplicitListReplacesTheLocalDefaultsInsteadOfAddingToThem() throws Exception {
        mockMvc.perform(options(CORPUS_ENDPOINT)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
