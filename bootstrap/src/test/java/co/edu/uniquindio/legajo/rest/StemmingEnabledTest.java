package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The stemming indicator switched on: it reaches the classic tokens and every response. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "legajo.preprocess.stemming=true")
@AutoConfigureMockMvc
class StemmingEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void traceTokensAreStemmedAndTheTraceReportsIt() throws Exception {
        mockMvc.perform(get("/api/v1/similarity/jaccard/trace")
                        .param("documentIdA", "d01").param("documentIdB", "d02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stemming").value(true))
                .andExpect(jsonPath("$.union").value(hasItem("studi")))
                .andExpect(jsonPath("$.union").value(not(hasItem("studies"))));
    }

    @Test
    void compareAndMatrixResultsReportStemming() throws Exception {
        mockMvc.perform(post("/api/v1/similarity/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentIdA":"d06","documentIdB":"d07","algorithmIds":["jaccard"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result.stemming").value(true));
        mockMvc.perform(post("/api/v1/similarity/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"algorithmId":"jaccard","documentIds":["d01","d02","d03"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1][1].stemming").value(true));
    }

    @Test
    void clusteringAndEvaluationReportStemming() throws Exception {
        mockMvc.perform(post("/api/v1/clustering").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemming").value(true));
        mockMvc.perform(post("/api/v1/clustering/evaluation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stemming").value(true));
    }
}
