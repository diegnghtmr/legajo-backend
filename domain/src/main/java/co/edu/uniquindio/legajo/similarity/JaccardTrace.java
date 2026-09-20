package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Set-based trace for the {@code jaccard} capability (TRD §6.3; PRD HU-1.6): the two token
 * sets {@code S_A}/{@code S_B}, their intersection and union with explicit sizes, and the
 * resulting coefficient.
 *
 * <p><b>Deterministic ordering (chosen for this trace).</b> The TRD fixes a backtrace tie
 * order for the two DP traces but does not fix an order for set listings — sets carry no
 * inherent order, and the token streams' original order is lost once each side is reduced to
 * a set. This trace publishes {@code setA}, {@code setB}, {@code intersection}, and
 * {@code union} sorted ascending by Java's natural {@link String} order
 * ({@link String#compareTo(String)}, i.e. plain lexicographic/code-unit order), independent
 * of either input's token order, so two runs over the same two sets always publish identical
 * listings.
 *
 * <p>Validated as plain, read-only data (mirroring {@link DpMatrixTrace}'s style): every
 * listed set must be sorted ascending with no duplicates, {@code intersectionSize}/{@code
 * unionSize} must match their listing's size, {@code intersection} must equal
 * {@code setA ∩ setB}, {@code union} must equal {@code setA ∪ setB}, and {@code coefficient}
 * must equal {@code intersectionSize / unionSize} (or {@code 1.0} when {@code unionSize} is
 * 0, the both-empty convention TRD §6.3 fixes for the otherwise-undefined 0/0 case).
 */
public record JaccardTrace(
        String algorithmId,
        List<String> setA,
        List<String> setB,
        int intersectionSize,
        int unionSize,
        List<String> intersection,
        List<String> union,
        double coefficient) implements AlgorithmTrace {

    private static final double TOLERANCE = 1e-9;

    public JaccardTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(setA, "setA");
        Objects.requireNonNull(setB, "setB");
        Objects.requireNonNull(intersection, "intersection");
        Objects.requireNonNull(union, "union");

        setA = List.copyOf(setA);
        setB = List.copyOf(setB);
        intersection = List.copyOf(intersection);
        union = List.copyOf(union);

        requireSortedNoDuplicates(setA, "setA");
        requireSortedNoDuplicates(setB, "setB");
        requireSortedNoDuplicates(intersection, "intersection");
        requireSortedNoDuplicates(union, "union");

        if (intersectionSize != intersection.size()) {
            throw new IllegalArgumentException(
                    "intersectionSize must equal intersection.size() (%d), was %d"
                            .formatted(intersection.size(), intersectionSize));
        }
        if (unionSize != union.size()) {
            throw new IllegalArgumentException(
                    "unionSize must equal union.size() (%d), was %d".formatted(union.size(), unionSize));
        }

        List<String> expectedIntersection = sortedIntersection(setA, setB);
        if (!intersection.equals(expectedIntersection)) {
            throw new IllegalArgumentException("intersection must equal setA ∩ setB, was " + intersection
                    + " but setA ∩ setB is " + expectedIntersection);
        }
        List<String> expectedUnion = sortedUnion(setA, setB);
        if (!union.equals(expectedUnion)) {
            throw new IllegalArgumentException(
                    "union must equal setA ∪ setB, was " + union + " but setA ∪ setB is " + expectedUnion);
        }

        double expectedCoefficient = unionSize == 0 ? 1.0 : (double) intersectionSize / unionSize;
        if (NumericGuards.isOutOfTolerance(coefficient, expectedCoefficient, TOLERANCE)) {
            throw new IllegalArgumentException(
                    "coefficient must equal intersectionSize/unionSize (%.12f), was %.12f"
                            .formatted(expectedCoefficient, coefficient));
        }
    }

    private static void requireSortedNoDuplicates(List<String> values, String fieldName) {
        for (int i = 1; i < values.size(); i++) {
            int comparison = values.get(i - 1).compareTo(values.get(i));
            if (comparison == 0) {
                throw new IllegalArgumentException(
                        "%s must not contain duplicates, repeated: %s".formatted(fieldName, values.get(i)));
            }
            if (comparison > 0) {
                throw new IllegalArgumentException(
                        "%s must be sorted ascending (natural String order)".formatted(fieldName));
            }
        }
    }

    private static List<String> sortedIntersection(List<String> setA, List<String> setB) {
        TreeSet<String> intersection = new TreeSet<>(setA);
        intersection.retainAll(setB);
        return new ArrayList<>(intersection);
    }

    private static List<String> sortedUnion(List<String> setA, List<String> setB) {
        TreeSet<String> union = new TreeSet<>(setA);
        union.addAll(setB);
        return new ArrayList<>(union);
    }
}
