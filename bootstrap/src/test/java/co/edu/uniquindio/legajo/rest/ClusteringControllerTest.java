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
 * The three clustering endpoints: {@code
 * POST /clustering}, {@code POST /clustering/evaluation}, {@code POST /clustering/cut}.
 * The real-corpus assertions, and coverage of both embedding
 * representations, live in
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

    /** {@code representation} lives in the body, so an unknown value is 400 with
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
     * {@code linkages} is a body field on every clustering endpoint
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

    @Test
    void runAppliesTheDefaultsWhenTheBodyIsOmitted() throws Exception {
        mockMvc.perform(post("/api/v1/clustering"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].linkageId").value(hasItems("single", "complete", "average", "ward")));
    }

    @Test
    void evaluationAppliesTheDefaultsWhenTheBodyIsOmitted() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].linkageId").value(hasItems("single", "complete", "average", "ward")));
    }

    @Test
    void runReportsStemmingOffByDefaultOnEveryLinkage() throws Exception {
        mockMvc.perform(post("/api/v1/clustering").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemming").value(false))
                .andExpect(jsonPath("$[3].stemming").value(false));
    }

    @Test
    void evaluationReportsStemmingOffByDefaultOnEveryLinkage() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemming").value(false))
                .andExpect(jsonPath("$[3].stemming").value(false));
    }

    @Test
    void runAnswers400ForAnEmptyLinkagesList() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void runAnswers400ForANullElementInLinkages() throws Exception {
        mockMvc.perform(post("/api/v1/clustering")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":["ward",null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void evaluationAnswers400ForAnEmptyLinkagesList() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void evaluationAnswers400ForABlankElementInLinkages() throws Exception {
        mockMvc.perform(post("/api/v1/clustering/evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linkages":[" "]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    /**
     * There is no request parameter {@code ks}, so no conforming request can alter the fixed-cuts
     * rule: there is no {@code ks}
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

    /** {@code /clustering/cut} is the only endpoint accepting a free {@code k}, and
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

    /** A missing {@code k} is this controller's own manual null check, not one of the contract's
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

    /** {@code linkage} is a body field here too, so an unknown one
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
