package co.edu.uniquindio.legajo.application.clustering;

import java.util.Objects;

/** The convenience shape {@code POST /clustering/evaluation} returns (TRD §6.6): the same
 * evaluation block {@code POST /clustering} computes, without the linkage matrix or leaf
 * order. */
public record LinkageEvaluationOnly(String linkageId, String linkageDisplayName, ClusteringEvaluationBlock evaluation) {

    public LinkageEvaluationOnly {
        Objects.requireNonNull(linkageId, "linkageId");
        Objects.requireNonNull(linkageDisplayName, "linkageDisplayName");
        Objects.requireNonNull(evaluation, "evaluation");
    }
}
