package co.edu.uniquindio.legajo.infrastructure.rest.clustering;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;

import java.util.List;
import java.util.Objects;

/** Wire shape of {@code POST /api/v1/clustering/cut} (TRD §6.6): one label per original
 * observation, plus the {@code k} they were cut at. */
public record ClusterAssignmentResponse(List<Integer> labels, int k) {

    public static ClusterAssignmentResponse from(ClusterAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        return new ClusterAssignmentResponse(assignment.labels(), assignment.k());
    }
}
