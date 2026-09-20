package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;

/**
 * Term-by-term trace for the {@code tfidf-cosine} capability (TRD §6.3; PRD HU-1.3): per
 * term, {@code f(t,d)}, {@code tf}, {@code df}, {@code idf}, raw weight, and normalized
 * weight ({@link TfIdfTermTrace}); the dot product; both raw (pre-normalization) vector
 * norms; the cosine; and the angle in degrees.
 *
 * <p><b>Term listing scope (documented reading of TRD §6.3/PRD HU-1.3).</b> Neither
 * document explicitly says whether the per-term listing spans the whole corpus vocabulary
 * or only the compared pair's; a term absent from both compared documents has {@code f = tf
 * = w = 0} on both sides and contributes nothing to the dot product or either norm, so it
 * carries no evidence. This trace therefore lists only terms present in at least one of the
 * two compared documents (the union of their token sets) — the interpretation
 * {@link TfIdfCosine} implements — sorted ascending by natural {@link String} order for
 * determinism, mirroring {@link JaccardTrace}'s ordering choice.
 *
 * <p><b>Dot product vs. cosine (TRD §6.3, "Fórmulas TF-IDF (fijadas)").</b> The fixed
 * formula states "el coseno es el producto punto de los vectores normalizados": once the
 * dot product is taken over the L2-normalized vectors (the "segundo paso explícito" the TRD
 * requires), it and the cosine are the same number. Both are kept as separate fields
 * because TRD §6.3/PRD HU-1.3 list "producto punto" and "coseno" as two separate evidence
 * items in the trace enumeration; {@code cosine} is validated to equal {@code dotProduct}.
 *
 * <p>Validated invariants: {@code terms} sorted ascending with no duplicate term names;
 * non-negative {@code corpusSize}/{@code rawNormA}/{@code rawNormB}; {@code dotProduct}
 * equal to the sum of each term's {@code normalizedWeightA * normalizedWeightB};
 * {@code rawNormA}/{@code rawNormB} equal to the L2 norm of the terms' raw weights on that
 * side; {@code cosine == dotProduct}; and {@code angleDegrees == degrees(acos(clamp(cosine,
 * -1, 1)))}. An empty {@code terms} list is valid (the TF-IDF null-vector degenerate case,
 * TRD §6.3, "Vector nulo de TF-IDF") with {@code rawNormA}/{@code rawNormB} at 0 for the
 * empty side(s) and {@code cosine}/{@code angleDegrees} carrying the fixed convention value
 * (1.0/0° for both-empty, 0.0/90° for exactly-one-empty).
 */
public record TfIdfCosineTrace(
        String algorithmId,
        int corpusSize,
        List<TfIdfTermTrace> terms,
        double dotProduct,
        double rawNormA,
        double rawNormB,
        double cosine,
        double angleDegrees) implements AlgorithmTrace {

    private static final double TOLERANCE = 1e-9;

    public TfIdfCosineTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(terms, "terms");
        terms = List.copyOf(terms);

        if (corpusSize < 0) {
            throw new IllegalArgumentException("corpusSize must not be negative, was " + corpusSize);
        }
        if (rawNormA < 0) {
            throw new IllegalArgumentException("rawNormA must not be negative, was " + rawNormA);
        }
        if (rawNormB < 0) {
            throw new IllegalArgumentException("rawNormB must not be negative, was " + rawNormB);
        }

        for (int i = 1; i < terms.size(); i++) {
            String previous = terms.get(i - 1).term();
            String current = terms.get(i).term();
            int comparison = previous.compareTo(current);
            if (comparison == 0) {
                throw new IllegalArgumentException("terms must not contain a duplicate term: " + current);
            }
            if (comparison > 0) {
                throw new IllegalArgumentException(
                        "terms must be sorted ascending (natural String order), '%s' came before '%s'"
                                .formatted(previous, current));
            }
        }

        // These three checks tie the aggregate fields to the per-term evidence, so they only
        // apply when there IS per-term evidence: an empty terms list is exclusively the
        // TF-IDF null-vector degenerate case (TfIdfCosine's compute()/trace() never touch
        // the corpus index in that case), whose dotProduct/cosine carry the fixed TRD §6.3
        // convention value (1.0 both-empty, 0.0 one-empty) rather than a derived sum-over-
        // zero-terms of 0.0.
        if (!terms.isEmpty()) {
            double expectedDotProduct = terms.stream()
                    .mapToDouble(t -> t.normalizedWeightA() * t.normalizedWeightB())
                    .sum();
            if (Math.abs(dotProduct - expectedDotProduct) > TOLERANCE) {
                throw new IllegalArgumentException(
                        "dotProduct must equal the sum of normalizedWeightA*normalizedWeightB over terms (%.12f), was %.12f"
                                .formatted(expectedDotProduct, dotProduct));
            }

            double expectedRawNormA =
                    Math.sqrt(terms.stream().mapToDouble(t -> t.rawWeightA() * t.rawWeightA()).sum());
            if (Math.abs(rawNormA - expectedRawNormA) > TOLERANCE) {
                throw new IllegalArgumentException(
                        "rawNormA must equal the L2 norm of the terms' rawWeightA (%.12f), was %.12f"
                                .formatted(expectedRawNormA, rawNormA));
            }
            double expectedRawNormB =
                    Math.sqrt(terms.stream().mapToDouble(t -> t.rawWeightB() * t.rawWeightB()).sum());
            if (Math.abs(rawNormB - expectedRawNormB) > TOLERANCE) {
                throw new IllegalArgumentException(
                        "rawNormB must equal the L2 norm of the terms' rawWeightB (%.12f), was %.12f"
                                .formatted(expectedRawNormB, rawNormB));
            }
        }

        if (Math.abs(cosine - dotProduct) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "cosine must equal dotProduct (TRD §6.3: coseno = producto punto de los vectores normalizados), "
                            + "dotProduct was %.12f, cosine was %.12f".formatted(dotProduct, cosine));
        }

        double clampedCosine = Math.max(-1.0, Math.min(1.0, cosine));
        double expectedAngle = Math.toDegrees(Math.acos(clampedCosine));
        if (Math.abs(angleDegrees - expectedAngle) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "angleDegrees must equal degrees(acos(clamp(cosine))) (%.12f), was %.12f"
                            .formatted(expectedAngle, angleDegrees));
        }
    }
}
