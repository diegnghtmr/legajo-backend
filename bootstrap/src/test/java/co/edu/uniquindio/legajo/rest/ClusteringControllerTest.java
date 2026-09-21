package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The three clustering endpoints of TRD §6.6 (feature doc task A4): {@code
 * POST /clustering}, {@code POST /clustering/evaluation}, {@code POST /clustering/cut}.
 * TAC-03/TAC-04/TAC-14's real-corpus assertions, and coverage of both embedding
 * representations (advisory {@code R3-clustering-embedding-path-untested}), live in
 * {@code ClusteringEndToEndTest}; this class covers per-endpoint shape and error
 * classification with MockMvc.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ClusteringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void runDefaultsToTfidfCosineAndAllFourLinkagesWhenTheBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].linkageId").value(hasItems("single", "complete", "average", "ward")));
    }

    @Test
    void runHonorsAnExplicitLinkagesSubset() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkages":["ward"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].linkageId").value("ward"))
                .andExpect(jsonPath("$[0].rows").isArray())
                .andExpect(jsonPath("$[0].leafOrder").isArray())
                .andExpect(jsonPath("$[0].evaluation.cophenetic").exists())
                .andExpect(jsonPath("$[0].evaluation.meanSilhouette").exists())
                .andExpect(jsonPath("$[0].evaluation.daviesBouldin").exists());
    }

    /** TRD §6.6: {@code representation} lives in the body, so an unknown value is 400 with
     * {@code unknown-representation}. */
    @Test
    void runAnswers400ForAnUnknownRepresentation() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"does-not-exist"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-representation"));
    }

    /**
     * TRD 1.3.7 §6.6, task A7: {@code linkages} is a body field on every clustering endpoint
     * (never a path segment, unlike a similarity algorithm id), so an unknown id is 400 with
     * {@code unknown-linkage} — changed from 404, since the URI itself is never wrong here.
     */
    @Test
    void runAnswers400ForAnUnknownLinkageId() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":["does-not-exist"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-linkage"));
    }

    /**
     * TRD §6.6: "no existe parámetro de solicitud {@code ks}, de modo que ninguna solicitud
     * conforme puede alterar la regla de cortes fijos de TAC-04". There is no {@code ks}
     * field on {@code ClusteringRequest}, so Jackson either ignores the unknown property or
     * (the actual, stricter, Spring Boot default) rejects the whole request outright — either
     * way, no request body can ever change which {@code k}s the evaluation is computed at.
     */
    @Test
    void anUnknownKsFieldNeverChangesTheFixedEvaluationCuts() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkages":["single"],"ks":[10,11,12]}
                                """))
                // Pinned to the behavior the server actually has: Jackson drops the unknown
                // field and the request succeeds. A test accepting "either 200 or 400" could
                // not detect a change from one to the other. What matters is that the cut set
                // is still exactly the fixed {2,3,4,5} for the reference corpus (n = 20), not
                // the requested {10,11,12}.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].evaluation.meanSilhouette.length()").value(4))
                .andExpect(jsonPath("$[0].evaluation.meanSilhouette['2']").exists())
                .andExpect(jsonPath("$[0].evaluation.meanSilhouette['5']").exists())
                .andExpect(jsonPath("$[0].evaluation.meanSilhouette['10']").doesNotExist());
    }

    @Test
    void evaluationReturnsOnlyTheEvaluationBlockPerLinkage() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].evaluation.cophenetic").exists())
                .andExpect(jsonPath("$[0].rows").doesNotExist())
                .andExpect(jsonPath("$[0].leafOrder").doesNotExist());
    }

    @Test
    void cutReturnsLabelsForTheRequestedK() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"average","k":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.k").value(3))
                .andExpect(jsonPath("$.labels").isArray());
    }

    /** TRD §6.6: {@code /clustering/cut} is the only endpoint accepting a free {@code k}, and
     * an out-of-range one is {@code invalid-cut}. */
    @Test
    void cutAnswers400ForAKOutsideTwoToNMinusOne() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"average","k":9999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:invalid-cut"));
    }

    /** A missing {@code k} is this controller's own manual null check, not one of TRD §6.6's
     * fixed-URN cases, so {@code type} stays absent ({@code about:blank}). */
    @Test
    void cutAnswers400WhenKIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"average"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void cutAnswers400ForAnUnknownRepresentation() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"does-not-exist","linkage":"average","k":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-representation"));
    }

    /** TRD 1.3.7 §6.6, task A7: {@code linkage} is a body field here too, so an unknown one
     * is 400 with {@code unknown-linkage} — changed from 404. */
    @Test
    void cutAnswers400ForAnUnknownLinkageId() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/cut")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representation":"tfidf-cosine","linkage":"does-not-exist","k":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-linkage"));
    }
}
