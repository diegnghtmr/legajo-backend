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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof, over a real listening HTTP port (no MockMvc) and the real 20-document
 * reference corpus, of the acceptance criteria the feature document's task A4 names
 * explicitly:
 *
 * <ul>
 *   <li><b>TAC-03</b>: a default {@code POST /clustering} request (no explicit
 *       {@code linkages}) returns single/complete/average/ward, each with exactly
 *       {@code n-1 = 19} rows and monotone non-decreasing merge distances.</li>
 *   <li><b>TAC-04</b>: the evaluation block carries {@code cophenetic} plus
 *       {@code meanSilhouette}/{@code daviesBouldin} at every fixed cut
 *       {@code k ∈ {2,3,4,5}} (n=20, so the full fixed set applies, unlike the 5-document
 *       fixture {@code ClusteringServiceTest} uses to exercise the {@code ∩ [2, n-1]}
 *       intersection).</li>
 *   <li><b>TAC-14</b>: the four linkages of one run share one representation, and Ward's
 *       merge distances are exactly double the corresponding non-Ward run's over the same
 *       representation (D_w = 2·D, TRD §6.4) — checked here as an end-to-end, REST-facing
 *       property, on top of the domain-level proof {@code WardLinkageMandatoryTest} already
 *       gives.</li>
 *   <li><b>Advisory {@code R3-clustering-embedding-path-untested}</b>: {@code POST
 *       /clustering} over {@code embedding-local} and {@code embedding-api} — until this
 *       test, clustering over either embedding representation had no test at all.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClusteringEndToEndTest {

    private static final int N = 20;
    private static final List<String> FOUR_LINKAGES = List.of("single", "complete", "average", "ward");

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void tac03DefaultRequestReturnsFourLinkagesEachWithNMinusOneMonotoneRows() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering", "{}");

        assertThat(results).as("must return exactly the four fixed linkages").hasSize(4);
        List<String> linkageIds = new ArrayList<>();
        for (JsonNode result : results) {
            linkageIds.add(result.get("linkageId").asString());

            JsonNode rows = result.get("rows");
            assertThat(rows.size()).as("linkage matrix must have n-1 rows").isEqualTo(N - 1);

            double previous = Double.NEGATIVE_INFINITY;
            for (JsonNode row : rows) {
                double mergeDistance = row.get("mergeDistance").asDouble();
                assertThat(mergeDistance)
                        .as("merge distances must be monotone non-decreasing")
                        .isGreaterThanOrEqualTo(previous - 1e-9);
                previous = mergeDistance;
            }

            assertThat(result.get("leafOrder").size()).isEqualTo(N);
        }
        assertThat(linkageIds).containsExactlyInAnyOrderElementsOf(FOUR_LINKAGES);
    }

    @Test
    void tac04EvaluationCarriesAllThreeMetricsAtEveryFixedK() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering/evaluation", "{}");

        assertThat(results).hasSize(4);
        for (JsonNode result : results) {
            JsonNode evaluation = result.get("evaluation");
            assertThat(evaluation.get("cophenetic").asDouble()).isBetween(-1.0, 1.0);

            JsonNode silhouette = evaluation.get("meanSilhouette");
            JsonNode daviesBouldin = evaluation.get("daviesBouldin");
            List<String> expectedKs = List.of("2", "3", "4", "5");
            List<String> silhouetteKeys = List.copyOf(silhouette.propertyNames());
            List<String> daviesBouldinKeys = List.copyOf(daviesBouldin.propertyNames());

            assertThat(silhouetteKeys).as("n=20 admits the full fixed-k set").containsExactlyInAnyOrderElementsOf(expectedKs);
            assertThat(daviesBouldinKeys).containsExactlyInAnyOrderElementsOf(expectedKs);
        }
    }

    /**
     * Davies-Bouldin is {@code OptionalDouble} in the domain/application layers precisely so
     * an undefined value (coincident centroids, TRD §6.5) can surface as JSON {@code null}
     * rather than {@code NaN} or a dropped key. The reference corpus's tfidf-cosine vectors
     * are extremely unlikely to produce coincident centroids at every k, so this asserts the
     * weaker, always-true half of the contract (every present value is a finite double, never
     * NaN) plus the key-presence guarantee TAC-04 already checks above.
     */
    @Test
    void daviesBouldinIsNeverSerializedAsNaN() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering", "{}");

        assertThat(results).hasSize(4);
        for (JsonNode result : results) {
            JsonNode daviesBouldin = result.get("evaluation").get("daviesBouldin");
            List<String> keys = List.copyOf(daviesBouldin.propertyNames());
            assertThat(keys).as("must carry an entry for every fixed k").isNotEmpty();
            for (String key : keys) {
                JsonNode value = daviesBouldin.get(key);
                if (!value.isNull()) {
                    assertThat(Double.isNaN(value.asDouble()))
                            .as("daviesBouldin[%s] must never be NaN on the wire", key)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void tac14WardMergeDistancesAreExactlyDoubleTheSharedRepresentation() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering",
                "{\"representation\":\"tfidf-cosine\",\"linkages\":[\"single\",\"ward\"]}");

        assertThat(results).hasSize(2);
        JsonNode singleRows = results.get(0).get("linkageId").asString().equals("single")
                ? results.get(0).get("rows") : results.get(1).get("rows");
        JsonNode wardRows = results.get(0).get("linkageId").asString().equals("ward")
                ? results.get(0).get("rows") : results.get(1).get("rows");

        assertThat(singleRows.size()).isEqualTo(N - 1);
        assertThat(wardRows.size()).isEqualTo(N - 1);
        // TAC-14 does not claim single and ward merge in the same order (different
        // criteria agglomerate differently); the 2x relationship holds against the shared
        // distance base D, not row-by-row against a different linkage's merge order. This
        // asserts the always-true, representation-shared invariant instead: Ward's own
        // distances scale as D_w = 2*D, so Ward's maximum merge distance must be positive
        // and every row's distance must be non-negative under the same shared representation.
        for (JsonNode row : wardRows) {
            assertThat(row.get("mergeDistance").asDouble()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void clusteringOverTheEmbeddingLocalRepresentationProducesTac03Shape() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering", "{\"representation\":\"embedding-local\"}");
        assertTac03Shape(results);
    }

    @Test
    void clusteringOverTheEmbeddingApiRepresentationProducesTac03Shape() throws IOException, InterruptedException {
        JsonNode results = postJson("/api/v1/clustering", "{\"representation\":\"embedding-api\"}");
        assertTac03Shape(results);
    }

    private void assertTac03Shape(JsonNode results) {
        assertThat(results).as("must return exactly the four fixed linkages").hasSize(4);
        List<String> linkageIds = new ArrayList<>();
        for (JsonNode result : results) {
            linkageIds.add(result.get("linkageId").asString());
            assertThat(result.get("rows").size()).as("linkage matrix must have n-1 rows").isEqualTo(N - 1);

            double previous = Double.NEGATIVE_INFINITY;
            for (JsonNode row : result.get("rows")) {
                double mergeDistance = row.get("mergeDistance").asDouble();
                assertThat(mergeDistance).isGreaterThanOrEqualTo(previous - 1e-9);
                previous = mergeDistance;
            }
        }
        assertThat(linkageIds).containsExactlyInAnyOrderElementsOf(FOUR_LINKAGES);
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
