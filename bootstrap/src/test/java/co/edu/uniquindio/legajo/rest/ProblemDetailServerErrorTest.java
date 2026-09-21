package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The defect task A3b fixes (advisory {@code R3-broad-exception-mapping}, feature doc
 * {@code rest-api.md}): before this task, {@code ProblemDetailExceptionHandler} mapped
 * {@code IllegalArgumentException -> 400} and {@code NoSuchElementException -> 404} broadly,
 * so a raw JDK exception thrown by a server-side bug (unrelated to request validation or a
 * missing resource) was reported to the client as its own mistake, complete with
 * {@code getMessage()} in the body. Only the dedicated
 * {@code InvalidRequestException}/{@code ResourceNotFoundException} subtypes are mapped to
 * 4xx now; a raw supertype falls through to the generic 500 — the honest answer, since the
 * server, not the client, is at fault.
 *
 * <p>{@link co.edu.uniquindio.legajo.rest.testsupport.BuggyTestOnlyController} is a real
 * {@code @RestController} on the test classpath, so this exercises the real handler through a
 * real HTTP request rather than asserting on Spring's exception-resolution rules in the
 * abstract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProblemDetailServerErrorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aRawIllegalArgumentExceptionFromABugIsA500NotA400() throws Exception {
        mockMvc.perform(get("/api/v1/test-only/raw-illegal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("simulated"))))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("IllegalArgumentException"))));
    }

    @Test
    void aRawNoSuchElementExceptionFromABugIsA500NotA404() throws Exception {
        mockMvc.perform(get("/api/v1/test-only/raw-no-such-element"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.type").doesNotExist())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("simulated"))))
                .andExpect(jsonPath("$.detail", not(org.hamcrest.Matchers.containsString("NoSuchElementException"))));
    }

    /**
     * TRD §6.6, task A7: malformed JSON is one of the framework-detected cases the TRD
     * explicitly keeps on Spring's standard {@code about:blank} handling — never one of this
     * task's fixed URNs, whatever endpoint receives it. Spring MVC's own {@code
     * HttpMessageNotReadableException} (mapped by the {@code ResponseEntityExceptionHandler}
     * base class) never reaches this project's own {@code @ExceptionHandler} methods, so
     * {@code type} is absent exactly as it is for a missing query parameter (checked in
     * {@code SimilarityControllerTest}: Jackson omits the key rather than writing a literal
     * {@code null}).
     */
    @Test
    void malformedJsonIsA400WithAboutBlankTypeNotAFixedUrn() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").doesNotExist());
    }
}
