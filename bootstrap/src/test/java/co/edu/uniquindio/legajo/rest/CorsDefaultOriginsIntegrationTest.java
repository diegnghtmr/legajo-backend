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
 * With {@code LEGAJO_CORS_ORIGINS} empty or absent, the server allows
 * exactly the two local development origins — {@code http://localhost:5173} (Vite dev
 * server) and {@code http://localhost} (the Compose frontend on :80) — and no other origin.
 * A real CORS preflight against a live controller under {@code /api/v1/**}
 * ({@link co.edu.uniquindio.legajo.infrastructure.rest.corpus.CorpusController}), through the
 * whole MVC CORS pipeline, not the lower-level {@code CorsRegistry} check in
 * {@code CorsWebConfigurationTest}. {@code legajo.cors-origins=} is set explicitly rather than
 * relying on an unset {@code LEGAJO_CORS_ORIGINS} in the environment running the build.
 *
 * <p>Requests target an absolute {@code http://legajo-api.test} URI rather than MockMvc's
 * default relative path: {@code CorsUtils.isCorsRequest} compares the {@code Origin} header
 * against the request's own scheme/host/port and treats an identical pair as same-origin
 * (no CORS processing, no header at all) — MockMvc's default host defaults to plain
 * {@code http://localhost}, which collides exactly with the Compose-origin test case below.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "legajo.cors-origins=")
@AutoConfigureMockMvc
class CorsDefaultOriginsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final URI CORPUS_ENDPOINT = URI.create("http://legajo-api.test/api/v1/corpus");

    @Test
    void viteDevServerOriginIsAllowedByDefault() throws Exception {
        mockMvc.perform(options(CORPUS_ENDPOINT)
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void composeFrontendOriginIsAllowedByDefault() throws Exception {
        mockMvc.perform(options(CORPUS_ENDPOINT)
                        .header("Origin", "http://localhost")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost"));
    }

    @Test
    void unlistedOriginIsNotAllowedByDefault() throws Exception {
        mockMvc.perform(options(CORPUS_ENDPOINT)
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
