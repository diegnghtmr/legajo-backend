package co.edu.uniquindio.legajo.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The document the server publishes must be the authored
 * {@code docs/openapi-legajo.yaml}, byte for byte. The build copies it onto the classpath
 * ({@code bootstrap/build.gradle.kts}, {@code processResources}); without this test, dropping
 * or misrouting that copy would still leave every conformance test green, because they read
 * the YAML from disk, while Swagger UI and the frontend's {@code api:types} got a 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiServingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theServerPublishesTheAuthoredContractUnchanged() throws Exception {
        String authored = Files.readString(Path.of("docs/openapi-legajo.yaml"), StandardCharsets.UTF_8);
        assertThat(authored).contains("openapi:");

        String served = mockMvc.perform(get("/openapi-legajo.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(served).isEqualTo(authored);
    }

    @Test
    void swaggerUiPointsAtTheAuthoredContract() throws Exception {
        String config = mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(config).contains("\"url\":\"/openapi-legajo.yaml\"");
    }
}
