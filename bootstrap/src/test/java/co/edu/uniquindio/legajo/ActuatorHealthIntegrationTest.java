package co.edu.uniquindio.legajo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves TAC-11 (TRD §15) end to end: {@code /actuator/health} must answer 200 from a real,
 * listening HTTP port, not merely be reachable in configuration. Before this feature added
 * {@code spring-boot-starter-web} (A1), {@code :bootstrap} carried no servlet container, so
 * {@code webEnvironment = RANDOM_PORT} could not even bind a listening port and this test
 * failed at Spring context startup — see the feature document's evidence for that observed
 * RED.
 *
 * <p>Uses the JDK's own {@link HttpClient} rather than {@code TestRestTemplate}: Spring Boot
 * 4.0.3's {@code spring-boot-starter-test} no longer pulls in a {@code TestRestTemplate}
 * (it moved to a separate, undeclared {@code spring-boot-restclient-test} module), so a real
 * socket call over the actually-bound {@link LocalServerPort} is both simpler and adds no
 * further dependency.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void actuatorHealthAnswers200OverRealHttp() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}
