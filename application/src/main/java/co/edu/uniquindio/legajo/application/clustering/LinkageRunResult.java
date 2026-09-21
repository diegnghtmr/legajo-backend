package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.clustering.LinkageStep;

import java.util.List;
import java.util.Objects;

/**
 * One linkage's full result within {@code POST /clustering} (TRD §6.6): the (n-1)-row
 * linkage matrix, the crossing-free {@code leafOrder} for a dendrogram, and its evaluation
 * block.
 */
public record LinkageRunResult(
        String linkageId,
        String linkageDisplayName,
        List<LinkageStep> rows,
        List<Integer> leafOrder,
        ClusteringEvaluationBlock evaluation) {

    public LinkageRunResult {
        Objects.requireNonNull(linkageId, "linkageId");
        Objects.requireNonNull(linkageDisplayName, "linkageDisplayName");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(leafOrder, "leafOrder");
        Objects.requireNonNull(evaluation, "evaluation");
        rows = List.copyOf(rows);
        leafOrder = List.copyOf(leafOrder);
    }
}
