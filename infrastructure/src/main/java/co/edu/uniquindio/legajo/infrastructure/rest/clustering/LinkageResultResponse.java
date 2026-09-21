package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;

import java.util.List;
import java.util.Objects;

/**
 * One linkage's full result within {@code POST /api/v1/clustering} (TRD §6.6): the
 * (n-1)-row linkage matrix, the crossing-free {@code leafOrder}, and its evaluation block.
 */
public record LinkageResultResponse(
        String linkageId,
        String linkageDisplayName,
        List<LinkageStepResponse> rows,
        List<Integer> leafOrder,
        ClusteringEvaluationResponse evaluation) {

    public static LinkageResultResponse from(LinkageRunResult result) {
        Objects.requireNonNull(result, "result");
        return new LinkageResultResponse(
                result.linkageId(),
                result.linkageDisplayName(),
                result.rows().stream().map(LinkageStepResponse::from).toList(),
                result.leafOrder(),
                ClusteringEvaluationResponse.from(result.evaluation()));
    }
}
