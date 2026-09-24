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
 * Primary PDF metadata extractor: POSTs the PDF to GROBID's
 * {@code /api/processHeaderDocument} as {@code multipart/form-data} (field {@code
 * "input"}) and parses the returned TEI with {@link GrobidTeiParser}. Only reachable
 * under Docker Compose's {@code ingest} profile; {@link
 * FallbackPdfMetadataExtractor} is what makes a GROBID outage or an empty abstract
 * non-fatal for the ingestion run as a whole.
 *
 * <p>When the header abstract looks {@linkplain AbstractQualityCheck suspicious}
 * (too short, contaminated, or cut mid-clause — the {@code d04}/{@code d14} regressions
 * found in the reference corpus), this extractor makes one best-effort retry against
 * {@code /api/processFulltextDocument}, which segments the header and body separately
 * and, for some layouts, produces a cleaner or more complete abstract. The retry never
 * turns a working extraction into a failure: any problem with the fulltext call (a
 * non-200 status, a network error, or a still-suspicious result) just keeps the header
 * result, and {@link AbstractQualityCheck#pickBetter} decides between the two when both
 * succeed.
 */
public final class GrobidPdfMetadataExtractor implements PdfMetadataExtractor {

    static final String EXTRACTED_BY = "GROBID";

    private final URI processHeaderEndpoint;
    private final URI processFulltextEndpoint;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public GrobidPdfMetadataExtractor(String grobidBaseUrl) {
        this(grobidBaseUrl, Duration.ofSeconds(60));
    }

    public GrobidPdfMetadataExtractor(String grobidBaseUrl, Duration requestTimeout) {
        Objects.requireNonNull(grobidBaseUrl, "grobidBaseUrl");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        String base = stripTrailingSlash(grobidBaseUrl);
        this.processHeaderEndpoint = URI.create(base + "/api/processHeaderDocument");
        this.processFulltextEndpoint = URI.create(base + "/api/processFulltextDocument");
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    @Override
    public ExtractedPdfMetadata extract(Path pdfPath) {
        Objects.requireNonNull(pdfPath, "pdfPath");
        ExtractedPdfMetadata headerResult = toMetadata(requestTei(pdfPath, processHeaderEndpoint));

        if (!AbstractQualityCheck.assess(headerResult.abstractText()).suspicious()) {
            return headerResult;
        }
        try {
            GrobidTeiParser.TeiExtraction fulltext = requestTei(pdfPath, processFulltextEndpoint);
            ExtractedPdfMetadata fulltextResult = new ExtractedPdfMetadata(
                    headerResult.title(),
                    headerResult.authors(),
                    IngestionTextCleaner.clean(fulltext.abstractText()),
                    EXTRACTED_BY);
            return AbstractQualityCheck.pickBetter(headerResult, fulltextResult);
        } catch (PdfExtractionException e) {
            return headerResult;
        }
    }

    private GrobidTeiParser.TeiExtraction requestTei(Path pdfPath, URI endpoint) {
        try {
            byte[] pdfBytes = Files.readAllBytes(pdfPath);
            String boundary = "legajo-boundary-" + UUID.randomUUID();
            byte[] body = MultipartFormDataBody.build(boundary, "input", pdfPath.getFileName().toString(), pdfBytes);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/xml")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PdfExtractionException(
                        "GROBID returned HTTP %d for %s (%s)".formatted(response.statusCode(), pdfPath, endpoint));
            }
            return GrobidTeiParser.parse(response.body());
        } catch (IOException e) {
            throw new PdfExtractionException("GROBID extraction failed for " + pdfPath, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PdfExtractionException("GROBID extraction interrupted for " + pdfPath, e);
        }
    }

    private static ExtractedPdfMetadata toMetadata(GrobidTeiParser.TeiExtraction extraction) {
        return new ExtractedPdfMetadata(
                IngestionTextCleaner.clean(extraction.title()),
                extraction.authors(),
                IngestionTextCleaner.clean(extraction.abstractText()),
                EXTRACTED_BY);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
