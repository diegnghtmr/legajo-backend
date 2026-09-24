package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A network failure or missing API key on a
 * path that needs a live embedding refresh must answer a 503 Problem Detail, never a 500 —
 * the cached-mode demo keeps working regardless of the live API's health.
 *
 * <p>No real request path throws
 * {@link co.edu.uniquindio.legajo.infrastructure.embedding.EmbeddingApiException} today
 * (every endpoint reads from the versioned JSON caches; that exception is only ever thrown
 * by the offline precompute CLI). This test therefore exercises the mapping through
 * {@link co.edu.uniquindio.legajo.rest.testsupport.BuggyTestOnlyController}'s dedicated
 * fixture endpoint, the same pattern {@code ProblemDetailServerErrorTest} already
 * established for the 500 mappings — proving the classification itself, ready for whichever
 * future serve-time path (if any) is authorized to throw it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProblemDetailDegradedServiceTest {

    @Autowired
    private MockMvc mockMvc;

    /** 503 always carries the fixed {@code embedding-api-unavailable}
     * URN — the only status/type pair in the table with no path-vs-body ambiguity at all. */
    @Test
    void anEmbeddingApiExceptionIsA503NotA500() throws Exception {
        mockMvc.perform(get("/api/v1/test-only/embedding-api-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:embedding-api-unavailable"))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("simulated"))))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("EmbeddingApiException"))));
    }
}
