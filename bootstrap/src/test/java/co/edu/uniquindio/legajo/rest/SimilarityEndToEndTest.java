package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * End-to-end proof, over a real listening HTTP port (no MockMvc), of this feature's
 * acceptance criteria:
 *
 * <ul>
 *   <li>A default {@code POST /similarity/compare} request (no
 *       {@code algorithmIds}) returns six rows, each carrying {@code degenerate}, and
 *       {@code GET /similarity/algorithms} returns exactly six ids including
 *       {@code needleman-wunsch}.</li>
 *   <li>Every exposed {@code normalizedScore} is within {@code [0,1]} (the
 *       ±1e-9 overshoot {@link co.edu.uniquindio.legajo.similarity.SimilarityResult}
 *       documents included), and {@code POST /similarity/matrix}'s diagonal is
 *       {@code 1.0 ± 1e-9} — asserted against the real reference corpus (n = 20), not a
 *       toy fixture.</li>
 *   <li>For each of the six capabilities, the value the trace endpoint
 *       exposes as "the computed value" (the DP matrix's bottom-right cell for the two DP
 *       algorithms, the coefficient/cosine/normalizedScore field for the other four)
 *       equals the {@code normalizedScore} (or, for the two DP algorithms, the
 *       {@code rawValue}) {@code POST /similarity/compare} publishes for the same pair.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarityEndToEndTest {

    private static final List<String> ALL_SIX_IDS = List.of(
            "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void defaultCompareReturnsSixRowsEachWithDegenerate() throws IOException, InterruptedException {
        JsonNode catalogue = getJson("/api/v1/similarity/algorithms");
        assertThat(catalogue).hasSize(6);
        List<String> catalogueIds = catalogue.valueStream().map(n -> n.get("id").asString()).toList();
        assertThat(catalogueIds).containsExactlyInAnyOrderElementsOf(ALL_SIX_IDS);
        assertThat(catalogueIds).contains("needleman-wunsch");

        JsonNode compareResults = postJson(
                "/api/v1/similarity/compare", "{\"documentIdA\":\"d01\",\"documentIdB\":\"d02\"}");

        assertThat(compareResults).hasSize(6);
        for (JsonNode row : compareResults) {
            assertThat(row.get("result").has("degenerate")).isTrue();
            assertThat(row.get("result").has("cached")).isTrue();
        }
    }

    /**
     * {@code cached} must be real, not always
     * {@code false}. This is deliberately its own test rather than folded into {@code
     * defaultCompareReturnsSixRowsEachWithDegenerate} above: {@code SimilarityService}'s cache is a Spring singleton shared by
     * every test in this class (and every other {@code @SpringBootTest} that boots the same
     * {@code RANDOM_PORT} context), so asserting "the very first call for this exact
     * (algorithm, pair) key is a miss" only holds for a key genuinely untouched by any other
     * test — {@code d16}/{@code d20} is reserved for exactly that, unused by every other
     * compare/matrix call in this test suite (checked by search, not assumed).
     */
    @Test
    void cachedIsFalseOnAFreshPairAndTrueOnAnIdenticalRepeatedCompare() throws IOException, InterruptedException {
        String body = "{\"documentIdA\":\"d16\",\"documentIdB\":\"d20\",\"algorithmIds\":[\"needleman-wunsch\"]}";

        JsonNode first = postJson("/api/v1/similarity/compare", body);
        assertThat(first.get(0).get("result").get("cached").asBoolean()).isFalse();

        JsonNode second = postJson("/api/v1/similarity/compare", body);
        assertThat(second.get(0).get("result").get("cached").asBoolean()).isTrue();
        assertThat(second.get(0).get("result").get("computedNanos").asLong())
                .as("a cache hit must report the original computation's measured time, not a fresh one")
                .isEqualTo(first.get(0).get("result").get("computedNanos").asLong());
    }

    @Test
    void everyExposedScoreIsInZeroOneAndTheMatrixDiagonalIsOne() throws IOException, InterruptedException {
        JsonNode compareResults = postJson(
                "/api/v1/similarity/compare", "{\"documentIdA\":\"d03\",\"documentIdB\":\"d09\"}");
        for (JsonNode row : compareResults) {
            double score = row.get("result").get("normalizedScore").asDouble();
            assertThat(score).isBetween(-1e-9, 1.0 + 1e-9);
        }

        String matrixBody = "{\"algorithmId\":\"tfidf-cosine\","
                + "\"documentIds\":[\"d01\",\"d02\",\"d03\",\"d04\",\"d05\"]}";
        JsonNode matrix = postJson("/api/v1/similarity/matrix", matrixBody);

        assertThat(matrix).hasSize(5);
        for (int i = 0; i < 5; i++) {
            JsonNode diagonalCell = matrix.get(i).get(i);
            assertThat(diagonalCell.get("normalizedScore").asDouble()).isCloseTo(1.0, offset(1e-9));
            for (int j = 0; j < 5; j++) {
                double cell = matrix.get(i).get(j).get("normalizedScore").asDouble();
                assertThat(cell).isBetween(-1e-9, 1.0 + 1e-9);
            }
        }
    }

    @Test
    void theTraceValueAlwaysEqualsThePublishedScore() throws IOException, InterruptedException {
        String documentIdA = "d05";
        String documentIdB = "d14";

        JsonNode compareResults = postJson("/api/v1/similarity/compare",
                "{\"documentIdA\":\"%s\",\"documentIdB\":\"%s\"}".formatted(documentIdA, documentIdB));

        // Every assertion below lives inside the loop, so an empty response would iterate
        // zero times and pass without checking a single trace. Pin the count first.
        assertThat(compareResults.size()).as("the default compare must return all six capabilities").isEqualTo(6);

        for (JsonNode row : compareResults) {
            String algorithmId = row.get("algorithmId").asString();
            JsonNode result = row.get("result");
            JsonNode trace = getJson("/api/v1/similarity/%s/trace?documentIdA=%s&documentIdB=%s"
                    .formatted(algorithmId, documentIdA, documentIdB));

            double traceValue = switch (algorithmId) {
                case "levenshtein", "needleman-wunsch" -> {
                    JsonNode matrix = trace.get("matrix");
                    JsonNode lastRow = matrix.get(matrix.size() - 1);
                    yield lastRow.get(lastRow.size() - 1).asDouble();
                }
                case "jaccard" -> trace.get("coefficient").asDouble();
                case "tfidf-cosine" -> trace.get("cosine").asDouble();
                case "embedding-local", "embedding-api" -> trace.get("normalizedScore").asDouble();
                default -> throw new AssertionError("unexpected algorithm id: " + algorithmId);
            };

            double publishedValue = switch (algorithmId) {
                case "levenshtein", "needleman-wunsch" -> result.get("rawValue").asDouble();
                default -> result.get("normalizedScore").asDouble();
            };

            assertThat(traceValue)
                    .as("trace value for %s must equal the published score", algorithmId)
                    .isCloseTo(publishedValue, offset(1e-9));
        }
    }

    private JsonNode getJson(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
        return jsonMapper.readTree(response.body());
    }

    private JsonNode postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("POST %s", path).isEqualTo(200);
        return jsonMapper.readTree(response.body());
    }
}
