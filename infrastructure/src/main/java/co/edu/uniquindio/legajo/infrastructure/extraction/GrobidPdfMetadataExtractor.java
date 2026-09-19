package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.infrastructure.ingest.IngestionTextCleaner;
import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Primary PDF metadata extractor (TRD §6.1, item 1; TRD §8): POSTs the PDF to GROBID's
 * {@code /api/processHeaderDocument} as {@code multipart/form-data} (field {@code
 * "input"}) and parses the returned TEI with {@link GrobidTeiParser}. Only reachable
 * under Docker Compose's {@code ingest} profile (TRD §14.1); {@link
 * FallbackPdfMetadataExtractor} is what makes a GROBID outage or an empty abstract
 * non-fatal for the ingestion run as a whole.
 */
public final class GrobidPdfMetadataExtractor implements PdfMetadataExtractor {

    static final String EXTRACTED_BY = "GROBID";

    private final URI processHeaderEndpoint;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GrobidPdfMetadataExtractor(String grobidBaseUrl) {
        this(grobidBaseUrl, Duration.ofSeconds(60));
    }

    public GrobidPdfMetadataExtractor(String grobidBaseUrl, Duration requestTimeout) {
        Objects.requireNonNull(grobidBaseUrl, "grobidBaseUrl");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.processHeaderEndpoint = URI.create(stripTrailingSlash(grobidBaseUrl) + "/api/processHeaderDocument");
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    @Override
    public ExtractedPdfMetadata extract(Path pdfPath) {
        Objects.requireNonNull(pdfPath, "pdfPath");
        try {
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String boundary = "legajo-boundary-" + UUID.randomUUID();
            byte[] body = MultipartFormDataBody.build(boundary, "input", pdfPath.getFileName().toString(), pdfBytes);

            HttpRequest request = HttpRequest.newBuilder(processHeaderEndpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PdfExtractionException(
                        "GROBID returned HTTP %d for %s".formatted(response.statusCode(), pdfPath));
            }

            GrobidTeiParser.TeiExtraction extraction = GrobidTeiParser.parse(response.body());
            return new ExtractedPdfMetadata(
                    IngestionTextCleaner.clean(extraction.title()),
                    extraction.authors(),
                    IngestionTextCleaner.clean(extraction.abstractText()),
                    EXTRACTED_BY);
        } catch (IOException e) {
            throw new PdfExtractionException("GROBID extraction failed for " + pdfPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfExtractionException("GROBID extraction interrupted for " + pdfPath, e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
