package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/corpus} and {@code GET /api/v1/corpus/{id}} (TRD §6.6, task A3): the
 * listing carries only {@code id}/{@code title}/{@code authors}, the detail endpoint adds
 * the full abstract, and an unknown id answers a 404 RFC 9457 Problem Detail rather than a
 * raw 500 or an unshaped error body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CorpusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsTheReferenceCorpusSummariesWithoutTheAbstract() throws Exception {
        mockMvc.perform(get("/api/v1/corpus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].id").value("d01"))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].authors").isArray())
                .andExpect(jsonPath("$[0].abstractText").doesNotExist());
    }

    @Test
    void detailReturnsTheFullDocumentIncludingTheAbstract() throws Exception {
        mockMvc.perform(get("/api/v1/corpus/{id}", "d01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("d01"))
                .andExpect(jsonPath("$.abstract").isNotEmpty());
    }

    /** TRD 1.3.7 §6.6, task A7: {@code id} is the path segment fixed by {@code
     * GET /corpus/{id}}, so this is 404 with {@code urn:legajo:problem:unknown-document}. */
    @Test
    void detailAnswers404ProblemDetailForAnUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/corpus/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").value("urn:legajo:problem:unknown-document"))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));
    }
}
