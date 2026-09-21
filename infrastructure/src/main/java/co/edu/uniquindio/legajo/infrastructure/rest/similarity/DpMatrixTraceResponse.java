package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.DpMatrixTrace;

import java.util.List;
import java.util.Objects;

/**
 * Wire shape of the DP trace shared by {@code levenshtein} and {@code needleman-wunsch}
 * (TRD §6.3, NFR-QA-03): the complete matrix, row/column token labels, the deterministic
 * optimal path, and the per-step operation classification. {@code matrix} is never
 * truncated — there is no truncation parameter on the trace endpoint (task A3 constraint,
 * NFR-QA-03).
 */
public record DpMatrixTraceResponse(
        String algorithmId,
        List<String> rowLabels,
        List<String> columnLabels,
        double[][] matrix,
        List<MatrixCellResponse> optimalPath,
        List<DpTraceStepResponse> operations) implements AlgorithmTraceResponse {

    public static DpMatrixTraceResponse from(DpMatrixTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new DpMatrixTraceResponse(
                trace.algorithmId(),
                trace.rowLabels(),
                trace.columnLabels(),
                trace.matrix(),
                trace.optimalPath().stream().map(MatrixCellResponse::from).toList(),
                trace.operations().stream().map(DpTraceStepResponse::from).toList());
    }
}
