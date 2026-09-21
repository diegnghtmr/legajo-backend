package co.edu.uniquindio.legajo.openapi;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.MockMvcResponse;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task A6's response-conformance half of TC-08: every documented operation's real MockMvc
 * response — success and every fixed-URN error TRD §6.6 assigns it — is validated against
 * {@code docs/openapi-legajo.yaml} with {@code openapi-request-validator-mockmvc} (a
 * maintained library explicitly "compatible with Spring 7+", confirmed here by actually
 * compiling and passing against this project's Spring Boot 4.0.3 / Jakarta MockMvc stack —
 * see the version catalog comment on {@code openapiRequestValidator} for the compatibility
 * check this task required before committing to it).
 *
 * <p>{@code /api/v1/test-only/**} (the fixture behind {@code ProblemDetailDegradedServiceTest}
 * / {@code ProblemDetailServerErrorTest}) is not a documented endpoint, so its 503/500
 * responses cannot be validated path-by-path here without dishonestly adding a fake
 * operation to the contract; the shared {@code ProblemDetail} schema those responses would
 * use is already exercised end to end by this class's own 400/404 cases below, which use the
 * exact same {@code $ref}.
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: this class reuses {@code d01}/{@code d02}/{@code
 * d03} across the similarity/clustering request-keyed caches (task A5) purely to exercise
 * response shapes, not caching behavior — but the {@code SimilarityService}/{@code
 * ClusteringService} caches are Spring singletons shared by every {@code @SpringBootTest}
 * that resolves to the same context (the same contamination risk {@code
 * SimilarityControllerTest}'s and {@code SimilarityEndToEndTest}'s Javadoc already document
 * for task A5). Without this annotation, whichever of those classes' own hardcoded
 * {@code cached=false} assertions runs *after* this class would observe a stale cache hit
 * from these calls and fail. Marking the context dirty after this class forces the next
 * class that needs it to start from a fresh, empty cache, regardless of run order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiContractConformanceTest {

    private static OpenApiInteractionValidator validator;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void loadContract() {
        String specUrl = new File("docs/openapi-legajo.yaml").getAbsoluteFile().toURI().toString();
        validator = OpenApiInteractionValidator.createForSpecificationUrl(specUrl).build();
    }

    /**
     * Validates only the response, not the request, against the contract. Used for the
     * handful of deliberately-invalid requests below (an unknown {@code algorithmIds}
     * entry, an unknown {@code linkages} entry, an unknown path {@code algorithmId}): those
     * requests intentionally violate this contract's own request-side {@code enum}
     * constraints (the closed set of valid ids), which the combined {@code
     * openApi().isValid(validator)} matcher would otherwise — correctly, but besides this
     * test's point — also flag as a request violation. This task's charter is response
     * conformance ("validate real MockMvc/HTTP responses ... against the YAML"); the
     * request-side enum is still exercised positively by every success-path test above and
     * below, which sends only valid ids.
     */
    private static void assertResponseConformsToContract(MvcResult result) {
        Request.Method method = Request.Method.valueOf(result.getRequest().getMethod());
        ValidationReport report = validator.validateResponse(
                result.getRequest().getRequestURI(), method, MockMvcResponse.of(result.getResponse()));
        assertThat(report.hasErrors())
                .as(SimpleValidationReportFormat.getInstance().apply(report))
                .isFalse();
    }

    @Test
    void corpusListConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/corpus"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void corpusDetailConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/corpus/{id}", "d01"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void corpusDetailUnknownIdConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/corpus/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void similarityAlgorithmsConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/algorithms"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void similarityCompareDefaultRequestConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02"}
                                """))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void similarityCompareUnknownAlgorithmConformsToTheContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d01","documentIdB":"d02","algorithmIds":["does-not-exist"]}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertResponseConformsToContract(result);
    }

    @Test
    void similarityMatrixConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02","d03"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void similarityMatrixInvalidSelectionConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(validator));
    }

    /** Exercises the {@code AlgorithmTrace} discriminator's mapping for all six capabilities
     * (TRD §6.3, NFR-QA-03): each must validate against its own {@code oneOf} branch, never
     * a sibling's. */
    @ParameterizedTest
    @ValueSource(strings = {
            "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api"
    })
    void similarityTraceConformsToTheContractForEveryCapability(String algorithmId) throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", algorithmId)
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void similarityTraceUnknownAlgorithmConformsToTheContract() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "does-not-exist")
                        .param("documentIdA", "d01")
                        .param("documentIdB", "d02"))
                .andExpect(status().isNotFound())
                .andReturn();
        assertResponseConformsToContract(result);
    }

    @Test
    void similarityTraceUnknownDocumentConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/{algorithmId}/trace", "levenshtein")
                        .param("documentIdA", "does-not-exist")
                        .param("documentIdB", "d02"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void clusteringRunDefaultRequestConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void clusteringRunUnknownLinkageConformsToTheContract() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":["does-not-exist"]}
                                """))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertResponseConformsToContract(result);
    }

    @Test
    void clusteringEvaluationConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void clusteringCutConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"average","k":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void clusteringCutInvalidKConformsToTheContract() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"average","k":9999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void embeddingsStatusConformsToTheContract() throws Exception {
        mockMvc.perform(get("/api/v1/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }

    @Test
    void healthConformsToTheContract() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(validator));
    }
}
