package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The four similarity endpoints of TRD §6.6 (task A3): {@code POST /similarity/compare},
 * {@code POST /similarity/matrix}, {@code GET /similarity/{algorithmId}/trace}, and
 * {@code GET /similarity/algorithms}. TAC-01/TAC-05/TAC-06's real-corpus assertions live in
 * {@code SimilarityEndToEndTest}; this class covers per-endpoint shape and error mapping
 * with MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SimilarityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogueReturnsExactlyTheSixFixedAlgorithms() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/algorithms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem("needleman-wunsch")));
    }

    @Test
    void compareDefaultsToAllSixAlgorithmsWhenAlgorithmIdsIsOmitted() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].result.normalizedScore").exists())
                .andExpect(jsonPath("$[0].result.cached").value(false))
                .andExpect(jsonPath("$[0].result.degenerate").exists())
                .andExpect(jsonPath("$[0].result.computedNanos").exists());
    }

    @Test
    void compareHonorsAnExplicitAlgorithmIdsSubset() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["jaccard"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].algorithmId").value("jaccard"));
    }

    @Test
    void compareAnswers404ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["does-not-exist"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void compareAnswers404ForAnUnknownDocumentId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"does-not-exist"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void compareAnswers400ForABlankDocumentId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"","documentIdB":"d02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /**
     * Task A5 (feature doc {@code rest-api.md}): {@code cached} must be real. This class's
     * {@code SimilarityService} bean is a Spring singleton shared by every test method in
     * this class (and every other {@code @SpringBootTest(webEnvironment = MOCK)} +
     * {@code @AutoConfigureMockMvc} test, which resolves to the same cached Spring test
     * context), so this test reserves {@code d17}/{@code d19} — unused by every other
     * similarity call in this suite (checked, not assumed) — instead of asserting on a pair
     * another test might have already touched.
     */
    @Test
    void compareTwiceReportsCachedFalseThenTrueForTheSamePairAndAlgorithm() throws Exception {
        String body = """
                {"documentIdA":"d17","documentIdB":"d19","algorithmIds":["needleman-wunsch"]}
                """;

        mockMvc.perform(post("/api/v1/similarity/compare").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].result.cached").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))));

        mockMvc.perform(post("/api/v1/similarity/compare").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].result.cached").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))));
    }

    @Test
    void matrixReturnsAnMByMGridForOneAlgorithm() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02","d03"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].length()").value(3))
                .andExpect(jsonPath("$[0][0].normalizedScore").value(1.0))
                .andExpect(jsonPath("$[0][0].cached").value(false));
    }

    @Test
    void matrixAnswers400WhenTheSelectionIsSmallerThanThree() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void matrixAnswers400ForADuplicateDocumentIdInTheSelection() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d01","d02"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void matrixAnswers404ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"does-not-exist","documentIds":["d01","d02","d03"]}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void traceReturnsTheCompleteDpMatrixForLevenshtein() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmId").value("levenshtein"))
                .andExpect(jsonPath("$.matrix").isArray())
                .andExpect(jsonPath("$.optimalPath").isArray())
                .andExpect(jsonPath("$.operations").isArray());
    }

    @Test
    void traceReturnsTheJaccardSetTraceForJaccard() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "jaccard")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithmId").value("jaccard"))
                .andExpect(jsonPath("$.intersection").isArray())
                .andExpect(jsonPath("$.union").isArray())
                .andExpect(jsonPath("$.coefficient").exists());
    }

    @Test
    void traceAnswers400WhenARequiredQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "d01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void traceAnswers404ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "does-not-exist")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isNotFound());
    }

    @Test
    void traceHasNoTruncationParameter() throws Exception {
        // NFR-QA-03: the trace endpoint accepts no truncation parameter. An extra,
        // unrecognized query parameter is simply ignored by Spring MVC (not bound to
        // anything), so the full untruncated matrix is what a client always receives.
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02")
                        .param("maxRows", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrix.length()").value(org.hamcrest.Matchers.greaterThan(1)));
    }
}
