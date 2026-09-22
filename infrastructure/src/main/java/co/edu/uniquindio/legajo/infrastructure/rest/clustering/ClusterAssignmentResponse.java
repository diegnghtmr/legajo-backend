package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.application.clustering.ClusteringCutResult;

import java.util.List;
import java.util.Objects;

/** Wire shape of {@code POST /api/v1/clustering/cut} (TRD §6.6): one label per original
 * observation, the {@code k} they were cut at, and {@code documentIds} (TRD 1.3.9: length n,
 * aligned with {@code labels} — {@code documentIds[i]} is the document whose cluster is
 * {@code labels[i]}). */
public record ClusterAssignmentResponse(List<Integer> labels, int k, List<String> documentIds) {

    public static ClusterAssignmentResponse from(ClusteringCutResult result) {
        Objects.requireNonNull(result, "result");
        return new ClusterAssignmentResponse(
                result.assignment().labels(), result.assignment().k(), result.documentIds());
    }
}
