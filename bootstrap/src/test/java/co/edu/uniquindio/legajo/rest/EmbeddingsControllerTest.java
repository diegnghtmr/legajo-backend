package co.edu.uniquindio.legajo.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/embeddings/status} (TRD §6.6, fixed by TRD 1.3.6, feature doc task A4):
 * one response carrying both embedding families, one object per capability. {@code
 * embedding-local} carries {@code device}; {@code embedding-api} carries {@code mode}; both
 * carry {@code provider}, {@code model}, {@code dimension}, {@code corpusSha256} and {@code
 * matchesCorpus} (TAC-13).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EmbeddingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusReportsBothEmbeddingFamiliesInOneResponse() throws Exception {
        mockMvc.perform(get("/api/v1/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingLocal.provider").exists())
                .andExpect(jsonPath("$.embeddingLocal.model").exists())
                .andExpect(jsonPath("$.embeddingLocal.dimension").exists())
                .andExpect(jsonPath("$.embeddingLocal.corpusSha256").exists())
                .andExpect(jsonPath("$.embeddingLocal.matchesCorpus").exists())
                .andExpect(jsonPath("$.embeddingLocal.device").exists())
                .andExpect(jsonPath("$.embeddingApi.provider").exists())
                .andExpect(jsonPath("$.embeddingApi.model").exists())
                .andExpect(jsonPath("$.embeddingApi.dimension").exists())
                .andExpect(jsonPath("$.embeddingApi.corpusSha256").exists())
                .andExpect(jsonPath("$.embeddingApi.matchesCorpus").exists())
                .andExpect(jsonPath("$.embeddingApi.mode").exists());
    }

    @Test
    void statusReportsMatchesCorpusTrueForTheVersionedReferenceCaches() throws Exception {
        // The versioned data/embeddings-*.json caches are bound to the reference corpus's
        // own corpusSha256 (TAC-13); on a correctly started server both must match.
        mockMvc.perform(get("/api/v1/embeddings/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingLocal.matchesCorpus").value(true))
                .andExpect(jsonPath("$.embeddingApi.matchesCorpus").value(true));
    }
}
