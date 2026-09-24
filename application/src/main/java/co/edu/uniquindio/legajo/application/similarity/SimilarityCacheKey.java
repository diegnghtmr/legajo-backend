package co.edu.uniquindio.legajo.application.similarity;

import java.util.Objects;

/**
 * Cache key for one similarity computation: {@code (algorithmId,
 * documentIdA, documentIdB)}, directional. It is ambiguous about
 * whether {@code (A,B)} and {@code (B,A)} share one entry; no {@link
 * co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm#compute} in this codebase is
 * proven symmetric by test, so this key never assumes it — it caches exactly the pair order
 * it was asked with. Every current caller ({@code compare}, {@code matrix}) always asks in
 * one fixed order per cell, so this loses no achievable cache reuse.
 */
public record SimilarityCacheKey(String algorithmId, String documentIdA, String documentIdB) {

    public SimilarityCacheKey {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(documentIdA, "documentIdA");
        Objects.requireNonNull(documentIdB, "documentIdB");
    }
}
