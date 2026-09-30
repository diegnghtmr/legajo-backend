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
 * The four similarity endpoints: {@code POST /similarity/compare},
 * {@code POST /similarity/matrix}, {@code GET /similarity/{algorithmId}/trace}, and
 * {@code GET /similarity/algorithms}. The real-corpus assertions live in
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

    /**
     * {@code algorithmIds} is a request-body field, so an unknown id
     * is 400 with {@code urn:legajo:problem:unknown-algorithm} — not 404, since the URI
     * {@code /api/v1/similarity/compare} itself exists (RFC 9110's 404 is about the target
     * resource, not a value inside the request).
     */
    @Test
    void compareAnswers400ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["does-not-exist"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-algorithm"));
    }

    /** A {@code null} or blank element inside {@code algorithmIds} is a malformed request:
     * 400 without a fixed type, never a server error. */
    @Test
    void compareAnswers400ForANullElementInAlgorithmIds() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["jaccard",null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void compareAnswers400ForABlankElementInAlgorithmIds() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["  "]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void compareKeepsDefaultingToAllSixWhenAlgorithmIdsIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d13","documentIdB":"d15","algorithmIds":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void matrixAnswers400ForANullElementInDocumentIds() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01",null,"d03"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void matrixAnswers400ForABlankElementInDocumentIds() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","","d03"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    /** A body document id is 400 with {@code unknown-document}. */
    @Test
    void compareAnswers400ForAnUnknownDocumentId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"does-not-exist"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-document"));
    }

    /** A blank required field is this controller's own manual check, not one of the contract's
     * fixed-URN cases, so it keeps {@code type} absent — Jackson omits the key entirely,
     * Spring's {@code about:blank} default (see {@code traceAnswers400WhenARequiredQuery...}
     * for why {@code doesNotExist()}, not a literal null, is the right assertion). */
    @Test
    void compareAnswers400ForABlankDocumentId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"","documentIdB":"d02"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    /**
     * {@code cached} must be real. This class's
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
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].result.cached").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(false))));

        mockMvc.perform(post("/api/v1/similarity/compare").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[*].result.cached").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(true))));
    }

    /**
     * A matrix cell must reuse the exact cache entry
     * a prior {@code compare} call for the same (algorithm, pair) populated, over real HTTP —
     * {@code SimilarityServiceTest.matrixCellsShareTheSameCacheAsCompareByAlgorithmAndDirectionalPair}
     * already proves this at the application layer; this proves the REST boundary wires the
     * same singleton {@code SimilarityService} through to {@code /similarity/matrix}. This
     * class's {@code SimilarityService} bean is a Spring singleton shared by every test in
     * this suite (see {@code compareTwiceReportsCachedFalseThenTrueForTheSamePairAndAlgorithm}
     * above), so this test reserves {@code d10}/{@code d11}/{@code d12} — unused by every
     * other similarity call in this suite (checked, not assumed) — instead of the pair
     * {@code compareTwice...} already reserves.
     */
    @Test
    void matrixCellReportsCachedTrueAfterAPriorCompareOfTheSamePair() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d10","documentIdB":"d11","algorithmIds":["jaccard"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d10","d11","d12"]}
                                """))
                .andExpect(status().isOk())
                // Only the positive cell is asserted: the Spring test context, and so the
                // cache, is shared with other test classes, so a "still a miss" assertion on
                // any other cell would depend on test order.
                .andExpect(jsonPath("$[0][1].cached").value(true));
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

    /** A selection under 3 documents is {@code invalid-selection}. */
    @Test
    void matrixAnswers400WhenTheSelectionIsSmallerThanThree() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:invalid-selection"));
    }

    /**
     * An empty or missing selection is also smaller than three, so it carries the
     * same {@code invalid-selection} URN instead of a type-less 400.
     */
    @Test
    void matrixAnswers400WithInvalidSelectionForAnEmptySelection() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:invalid-selection"));
    }

    /** A duplicate id in the selection is the same {@code invalid-selection} URN. */
    @Test
    void matrixAnswers400ForADuplicateDocumentIdInTheSelection() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d01","d02"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:invalid-selection"));
    }

    /** {@code matrix}'s {@code algorithmId} is a body field, so an
     * unknown one is 400 with {@code unknown-algorithm} (never 404, unlike {@code trace}'s
     * path segment). */
    @Test
    void matrixAnswers400ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"does-not-exist","documentIds":["d01","d02","d03"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-algorithm"));
    }

    /** The unknown-document row applies to {@code matrix}'s body list explicitly too, not
     * just {@code compare} and {@code trace}. */
    @Test
    void matrixAnswers400ForAnUnknownDocumentId() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","does-not-exist","d03"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-document"));
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

    /**
     * A missing required query parameter is a framework-detected error (Spring MVC's own
     * {@code MissingServletRequestParameterException}, mapped by the {@code
     * ResponseEntityExceptionHandler} base class this project's handler extends), not a
     * violation of one of this contract's fixed rules — so it keeps Spring's standard {@code
     * about:blank} {@code type}, never one of the fixed URNs. RFC 9457 defines
     * {@code about:blank} as the default when {@code type} is omitted; Spring's {@code
     * ProblemDetail} leaves the field unset in that case, and — checked empirically here,
     * not assumed — {@code ProblemDetailJacksonMixin} serializes it by omitting the {@code
     * type} key entirely rather than writing a literal JSON {@code null}, so {@code
     * doesNotExist()} is the correct assertion for "absent" in the RFC 9457 sense.
     */
    @Test
    void traceAnswers400WhenARequiredQueryParameterIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "d01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    /**
     * {@code algorithmId} is the path segment fixed by {@code
     * GET /similarity/{algorithmId}/trace}, so it stays 404 — the one similarity case that
     * did not change — with {@code urn:legajo:problem:unknown-algorithm}.
     */
    @Test
    void traceAnswers404ForAnUnknownAlgorithmId() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "does-not-exist")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-algorithm"));
    }

    /** {@code documentIdA}/{@code documentIdB} are query parameters
     * on {@code trace}, so an unknown one is 400 with {@code unknown-document} — the same
     * URN {@code compare}/{@code matrix} use for a body id, but a different status because
     * this value is not part of the path. */
    @Test
    void traceAnswers400ForAnUnknownDocumentId() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "does-not-exist")
                        .param("documentIdB", "d02"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-document"));
    }

    @Test
    void traceHasNoTruncationParameter() throws Exception {
        // The trace endpoint accepts no truncation parameter. An extra,
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
