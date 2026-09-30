package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.LinkageEvaluationOnly;

import java.util.Objects;

/**
 * The convenience shape {@code POST /api/v1/clustering/evaluation} returns: the
 * same evaluation block {@code POST /clustering} computes, without the linkage matrix or
 * leaf order.
 */
public record LinkageEvaluationResponse(
        String linkageId, String linkageDisplayName, ClusteringEvaluationResponse evaluation, boolean stemming) {

    public static LinkageEvaluationResponse from(LinkageEvaluationOnly result, boolean stemming) {
        Objects.requireNonNull(result, "result");
        return new LinkageEvaluationResponse(
                result.linkageId(), result.linkageDisplayName(), ClusteringEvaluationResponse.from(result.evaluation()), stemming);
    }
}
