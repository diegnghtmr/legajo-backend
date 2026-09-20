package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One document's cached embedding vector (TRD §6.3/§9): {@code documentId} it belongs to,
 * {@code provider}/{@code model} metadata (e.g. {@code "local"}/{@code "all-MiniLM-L6-v2"}),
 * the L2-normalized {@code values}, and {@code preNormL2} — the norm of the raw pooled
 * vector <em>before</em> that normalization, kept only as precompute-time provenance (TRD
 * §6.3, "Invariante de norma unitaria (fijado)").
 *
 * <p><b>Structural invariant.</b> {@code values} must always be unit length (within 1e-9);
 * this type only ever holds an already-normalized vector, so every reader (the
 * {@code embedding-local} metric, its trace, a JSON adapter after renormalizing on load) can
 * rely on {@code values} being ready to dot-product directly. {@code preNormL2} is
 * deliberately unconstrained relative to 1 — it is typically a much larger number (e.g. the
 * TRD §9 example's {@code 5.814322}), because it is the norm of the vector <em>before</em>
 * normalization, not after.
 */
public record EmbeddingVector(String documentId, String provider, String model, double preNormL2,
        List<Double> values) {

    private static final double UNIT_NORM_TOLERANCE = 1e-9;

    public EmbeddingVector {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(values, "values");
        if (documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        NumericGuards.requireNonNegativeFinite(preNormL2, "preNormL2");
        values = List.copyOf(values);

        double norm = l2Norm(values);
        // NumericGuards.isOutOfTolerance rejects a non-finite norm explicitly: Math.abs(NaN
        // - 1.0) > UNIT_NORM_TOLERANCE is false, so a NaN norm (e.g. from a zero-norm cache
        // renormalization, 0.0/0.0) would otherwise pass a bare tolerance comparison
        // silently instead of failing closed. This is the exact bug shape NumericGuards
        // centralizes for the rest of this package.
        if (NumericGuards.isOutOfTolerance(norm, 1.0, UNIT_NORM_TOLERANCE)) {
            throw new IllegalArgumentException(
                    "values must be L2-normalized to unit length (within %.0e), norm was %.15f for document '%s'"
                            .formatted(UNIT_NORM_TOLERANCE, norm, documentId));
        }
    }

    /** The vector's dimension, i.e. {@code values.size()}. */
    public int dimension() {
        return values.size();
    }

    /** Hand-written L2 norm (TRD §3.3: normalization is not delegable under R-02). */
    public static double l2Norm(List<Double> vector) {
        Objects.requireNonNull(vector, "vector");
        double sumOfSquares = 0.0;
        for (double component : vector) {
            sumOfSquares += component * component;
        }
        return Math.sqrt(sumOfSquares);
    }

    /**
     * Builds a unit vector from {@code rawValues} by hand-computing their L2 norm and
     * dividing every component by it (TRD §6.3, "Invariante de norma unitaria (fijado)").
     * {@code preNormL2} records the pre-normalization norm as provenance, exactly as the
     * offline precompute job must (TRD §6.3, "Límite de tokens de MiniLM": "... registrando
     * preNormL2 antes de normalizar").
     */
    public static EmbeddingVector normalize(String documentId, String provider, String model,
            List<Double> rawValues) {
        Objects.requireNonNull(rawValues, "rawValues");
        double norm = l2Norm(rawValues);
        if (norm == 0.0) {
            throw new IllegalArgumentException(
                    "cannot L2-normalize an all-zero vector for document '" + documentId + "'");
        }
        List<Double> unit = new ArrayList<>(rawValues.size());
        for (double component : rawValues) {
            unit.add(component / norm);
        }
        return new EmbeddingVector(documentId, provider, model, norm, unit);
    }
}
