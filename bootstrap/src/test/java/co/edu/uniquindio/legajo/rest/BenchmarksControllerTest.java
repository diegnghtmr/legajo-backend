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
 * {@code GET /api/v1/benchmarks} (TRD §6.6, fixed by TRD 1.3.10, feature doc task J5): serves
 * the versioned {@code benchmarks/results/jmh-results.csv}/{@code slopes.csv} exports as-is.
 * Runs against the real Spring context, so this exercises the actual versioned CSVs
 * committed at {@code benchmarks/results/} (never a fixture) — the same "real corpus, not a
 * toy fixture" style {@code EmbeddingsControllerTest} already uses for its own reference-data
 * assertions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BenchmarksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTheHarnessResultsAndSlopesShape() throws Exception {
        mockMvc.perform(get("/api/v1/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harness.cpuModel").exists())
                .andExpect(jsonPath("$.harness.logicalCores").exists())
                .andExpect(jsonPath("$.harness.totalRamBytes").exists())
                .andExpect(jsonPath("$.harness.jdk").exists())
                .andExpect(jsonPath("$.harness.os").exists())
                .andExpect(jsonPath("$.harness.measuredAt").exists())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.slopes").isArray());
    }

    @Test
    void reportsRealReferenceHarnessAndTheSloClusteringFamilyWithAtLeastOneSlope() throws Exception {
        // TAC-07/TAC-18 evidence lives in these exact versioned files (odd/tasks/jmh-benchmarks.md,
        // J4): slo-clustering (NFR-QA-02) is one of the SLO families, and every curve family in
        // slopes.csv has at least one row, so both must survive the CSV -> JSON translation.
        mockMvc.perform(get("/api/v1/benchmarks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.harness.cpuModel").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
                .andExpect(jsonPath("$.results[?(@.family == 'slo-clustering')]").isNotEmpty())
                .andExpect(jsonPath("$.slopes[0].family").exists());
    }
}
