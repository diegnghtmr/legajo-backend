package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.clustering.LinkageStep;

import java.util.List;
import java.util.Objects;

/**
 * One linkage's full result within {@code POST /clustering}: the (n-1)-row
 * linkage matrix, the crossing-free {@code leafOrder} for a dendrogram, the document id
 * behind each observation index ({@code documentIds[i]} names the document of
 * observation {@code i}, in {@code corpus.json} order — the same order {@code idx1}/{@code
 * idx2} and {@code leafOrder} index into), and its evaluation block.
 */
public record LinkageRunResult(
        String linkageId,
        String linkageDisplayName,
        List<LinkageStep> rows,
        List<Integer> leafOrder,
        List<String> documentIds,
        ClusteringEvaluationBlock evaluation) {

    public LinkageRunResult {
        Objects.requireNonNull(linkageId, "linkageId");
        Objects.requireNonNull(linkageDisplayName, "linkageDisplayName");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(leafOrder, "leafOrder");
        Objects.requireNonNull(documentIds, "documentIds");
        Objects.requireNonNull(evaluation, "evaluation");
        rows = List.copyOf(rows);
        leafOrder = List.copyOf(leafOrder);
        documentIds = List.copyOf(documentIds);
    }
}
