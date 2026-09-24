package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.DpOperationKind;
import co.edu.uniquindio.legajo.similarity.DpTraceStep;

import java.util.Objects;

/** Wire shape of one classified DP backtrace step (match/mismatch/gap). */
public record DpTraceStepResponse(MatrixCellResponse from, MatrixCellResponse to, DpOperationKind operation) {

    public static DpTraceStepResponse from(DpTraceStep step) {
        Objects.requireNonNull(step, "step");
        return new DpTraceStepResponse(
                MatrixCellResponse.from(step.from()), MatrixCellResponse.from(step.to()), step.operation());
    }
}
