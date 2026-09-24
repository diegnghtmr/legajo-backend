package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.clustering.ClusterAssignment;

import java.util.List;
import java.util.Objects;

/**
 * {@code POST /clustering/cut}'s full result: the domain
 * {@link ClusterAssignment} plus {@code documentIds}, the document behind each labeled
 * observation, in {@code corpus.json} order — {@code documentIds.get(i)} is the document
 * whose cluster is {@code assignment.labels().get(i)}.
 *
 * <p>{@link ClusterAssignment} stays a pure domain type over abstract observation indices,
 * with no knowledge of documents or corpora; this application-layer wrapper attaches the
 * document ids the same way {@link LinkageRunResult} already does for {@code POST
 * /clustering} — both are populated from {@link ClusteringService}'s single corpus load, so
 * ids and indices always come from the same source.
 */
public record ClusteringCutResult(ClusterAssignment assignment, List<String> documentIds) {

    public ClusteringCutResult {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(documentIds, "documentIds");
        documentIds = List.copyOf(documentIds);
    }
}
