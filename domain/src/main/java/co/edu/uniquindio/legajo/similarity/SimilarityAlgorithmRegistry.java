package co.edu.uniquindio.legajo.similarity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Framework-free collector for every {@link SimilarityAlgorithm} the application wires
 * up. Infrastructure gathers the Spring {@code @Component} beans into a {@code List} via
 * list injection (TRD §4.4) and passes it to this constructor; the domain module itself
 * never depends on Spring.
 */
public final class SimilarityAlgorithmRegistry {

    private final Map<String, SimilarityAlgorithm> byId;
    private final List<SimilarityAlgorithm> all;

    public SimilarityAlgorithmRegistry(List<SimilarityAlgorithm> algorithms) {
        Objects.requireNonNull(algorithms, "algorithms");

        Map<String, SimilarityAlgorithm> registered = new LinkedHashMap<>();
        for (SimilarityAlgorithm algorithm : algorithms) {
            SimilarityAlgorithm previous = registered.putIfAbsent(algorithm.id(), algorithm);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate similarity algorithm id: " + algorithm.id());
            }
        }
        this.byId = Map.copyOf(registered);
        this.all = List.copyOf(registered.values());
    }

    /** Returns the algorithm registered under {@code id}, or throws if none matches. */
    public SimilarityAlgorithm require(String id) {
        Objects.requireNonNull(id, "id");
        SimilarityAlgorithm algorithm = byId.get(id);
        if (algorithm == null) {
            throw new NoSuchElementException("no similarity algorithm registered with id: " + id);
        }
        return algorithm;
    }

    /** Returns the algorithm registered under {@code id}, if any. */
    public Optional<SimilarityAlgorithm> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    /** Every registered algorithm, in registration order. */
    public List<SimilarityAlgorithm> all() {
        return all;
    }

    /** Registered algorithms whose {@link AlgorithmKind} matches {@code kind}, in registration order. */
    public List<SimilarityAlgorithm> ofKind(AlgorithmKind kind) {
        Objects.requireNonNull(kind, "kind");
        return all.stream().filter(algorithm -> algorithm.kind() == kind).toList();
    }
}
