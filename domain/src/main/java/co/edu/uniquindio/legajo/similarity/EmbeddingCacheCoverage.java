package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Rule for how well an embedding cache covers a corpus: it must hold exactly one vector for
 * every corpus document id, with no id missing, none unexpected and none repeated.
 */
public final class EmbeddingCacheCoverage {

    private EmbeddingCacheCoverage() {
    }

    /** Describes every coverage problem, or is empty when the cache covers the corpus exactly. */
    public static Optional<String> mismatch(Set<String> corpusIds, EmbeddingCache cache) {
        Objects.requireNonNull(corpusIds, "corpusIds");
        Objects.requireNonNull(cache, "cache");

        Set<String> seen = new HashSet<>();
        Set<String> duplicated = new TreeSet<>();
        for (EmbeddingVector vector : cache.vectors()) {
            if (!seen.add(vector.documentId())) {
                duplicated.add(vector.documentId());
            }
        }
        Set<String> missing = new TreeSet<>(corpusIds);
        missing.removeAll(seen);
        Set<String> unexpected = new TreeSet<>(seen);
        unexpected.removeAll(corpusIds);

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("missing vectors for " + missing);
        }
        if (!unexpected.isEmpty()) {
            problems.add("unexpected vectors for " + unexpected);
        }
        if (!duplicated.isEmpty()) {
            problems.add("duplicate vectors for " + duplicated);
        }
        return problems.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", problems));
    }
}
