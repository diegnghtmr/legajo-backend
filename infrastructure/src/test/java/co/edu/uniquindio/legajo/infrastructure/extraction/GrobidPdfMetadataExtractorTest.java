package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link GrobidPdfMetadataExtractor} end to end against a real (JDK built-in)
 * HTTP server standing in for GROBID, so the multipart request shape and the TEI
 * response parsing are both verified without needing Docker or WireMock in the unit
 * test suite. The optional Testcontainers integration test against the real GROBID
 * image is a separate, Docker-tagged test (see {@code T4} scope notes in the feature
 * doc); it is not required for {@code ./gradlew build}.
 */
class GrobidPdfMetadataExtractorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsTheePdfAsMultipartAndParsesTheTeiResponse(@TempDir Path tempDir) throws IOException {
        String tei = readFixture("extraction/sample-header.tei.xml");
        AtomicReference<String> receivedContentType = new AtomicReference<>();
        AtomicReference<byte[]> receivedBody = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/processHeaderDocument", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            byte[] response = tei.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/xml");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();

        Path pdf = tempDir.resolve("01.pdf");
        Files.write(pdf, "%PDF-1.4 fake pdf bytes".getBytes(StandardCharsets.UTF_8));

        GrobidPdfMetadataExtractor extractor = new GrobidPdfMetadataExtractor(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(5));

        ExtractedPdfMetadata metadata = extractor.extract(pdf);

        assertThat(receivedContentType.get()).startsWith("multipart/form-data; boundary=");
        assertThat(new String(receivedBody.get(), StandardCharsets.UTF_8))
                .contains("name=\"input\"")
                .contains("filename=\"01.pdf\"")
                .contains("%PDF-1.4 fake pdf bytes");
        assertThat(metadata.extractedBy()).isEqualTo("GROBID");
        assertThat(metadata.title()).isEqualTo("A Study of Information Retrieval Methods");
        assertThat(metadata.authors()).containsExactly("Ada Lovelace", "Grace Hopper");
        assertThat(metadata.abstractText())
                .isEqualTo("This paper studies several information retrieval methods over a "
                        + "small corpus. Results show consistent improvements across all methods.");
    }

    @Test
    void failsClosedWhenGrobidRespondsWithAnErrorStatus(@TempDir Path tempDir) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/processHeaderDocument", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        Path pdf = tempDir.resolve("01.pdf");
        Files.write(pdf, "not a real pdf".getBytes(StandardCharsets.UTF_8));

        GrobidPdfMetadataExtractor extractor = new GrobidPdfMetadataExtractor(
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(5));

        assertThatThrownBy(() -> extractor.extract(pdf)).isInstanceOf(PdfExtractionException.class);
    }

    private static String readFixture(String resourcePath) throws IOException {
        try (InputStream in = GrobidPdfMetadataExtractorTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
