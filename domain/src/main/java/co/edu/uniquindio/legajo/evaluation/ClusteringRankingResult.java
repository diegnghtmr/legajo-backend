package co.edu.uniquindio.legajo.evaluation;

import java.util.List;
import java.util.Objects;

/**
 * The full output of the ranking rule: every linkage's
 * evaluation, the cophenetic tie set, and the two leaders the interface/report must always
 * point to — {@code bestTreeFidelity} (the resolved cophenetic winner, i.e. the tree's best
 * fit) and {@code bestPartitionAtKRef} (the plain silhouette leader at {@code k_ref}, i.e.
 * the best partition). {@link ClusteringRanking} is this record's only intended
 * producer, but it re-validates its own cross-field invariants rather than trusting its
 * caller — the house rule this package's other validated value types already follow.
 *
 * <p>{@code sampleSize} carries the sample-size caveat that must always be stated
 * (n = |corpus|); the actual caveat text and the "both leaders" narrative
 * belong to a future presentation layer (REST/UI), out of scope here — this record
 * only guarantees the underlying facts are self-consistent.
 */
public record ClusteringRankingResult(
        List<LinkageEvaluation> evaluations,
        List<LinkageEvaluation> copheneticTieSet,
        LinkageEvaluation bestTreeFidelity,
        LinkageEvaluation bestPartitionAtKRef,
        boolean leadersDiffer,
        int sampleSize) {

    public ClusteringRankingResult {
        Objects.requireNonNull(evaluations, "evaluations");
        evaluations = List.copyOf(evaluations);
        Objects.requireNonNull(copheneticTieSet, "copheneticTieSet");
        copheneticTieSet = List.copyOf(copheneticTieSet);
        Objects.requireNonNull(bestTreeFidelity, "bestTreeFidelity");
        Objects.requireNonNull(bestPartitionAtKRef, "bestPartitionAtKRef");

        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("evaluations must not be empty");
        }
        if (copheneticTieSet.isEmpty()) {
            throw new IllegalArgumentException("copheneticTieSet must not be empty");
        }
        if (!evaluations.containsAll(copheneticTieSet)) {
            throw new IllegalArgumentException("copheneticTieSet must be a subset of evaluations");
        }
        if (!copheneticTieSet.contains(bestTreeFidelity)) {
            throw new IllegalArgumentException("bestTreeFidelity must be a member of copheneticTieSet");
        }
        if (!evaluations.contains(bestPartitionAtKRef)) {
            throw new IllegalArgumentException("bestPartitionAtKRef must be a member of evaluations");
        }

        // leadersDiffer is caller-supplied but re-derived and checked here rather than
        // trusted, by the two leaders' stable id() — whether the silhouette leader at k_ref
        // differs from the cophenetic leader — never by reference identity, since two
        // LinkageEvaluation instances describing the same criterion are not guaranteed to
        // share a LinkageCriterion instance.
        boolean expectedLeadersDiffer =
                !bestTreeFidelity.criterion().id().equals(bestPartitionAtKRef.criterion().id());
        if (leadersDiffer != expectedLeadersDiffer) {
            throw new IllegalArgumentException(
                    "leadersDiffer (%s) is inconsistent with bestTreeFidelity/bestPartitionAtKRef (expected %s)"
                            .formatted(leadersDiffer, expectedLeadersDiffer));
        }

        if (sampleSize < 1) {
            throw new IllegalArgumentException("sampleSize must be at least 1, was " + sampleSize);
        }
    }
}
