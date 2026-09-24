package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

import java.util.Objects;

/**
 * Hand-written Pearson correlation (no library implements it: no statistics or
 * linear-algebra library), the general-purpose primitive {@link CopheneticCorrelation}
 * builds on to compare a tree's cophenetic distances against D.
 *
 * <p>Uses the two-pass centered form: take each array's mean, then accumulate the centered
 * products and squares. The one-pass {@code r = (n*Sxy - Sx*Sy) / sqrt(...)} form is
 * algebraically equivalent but unusable here for two reasons. Its variance term scales with
 * the data, so a zero-variance guard against any absolute epsilon rejects legitimate
 * small-magnitude input — and cophenetic correlation is fed cosine distances that get that
 * small when the corpus holds near-duplicate abstracts. It also subtracts two large nearly
 * equal quantities, losing precision to cancellation. Centering first removes both problems
 * and makes zero variance an exact algebraic test rather than a thresholded one.
 */
public final class PearsonCorrelation {


    private PearsonCorrelation() {
    }

    /**
     * Pearson's r between {@code x} and {@code y}, taken pairwise (index-aligned). Requires
     * at least two values and non-zero variance in both arrays — a zero-variance array would
     * otherwise divide the denominator to {@code 0.0} and silently return {@code NaN} or
     * {@code Infinity} instead of failing closed, the same shape of bug
     * {@link NumericGuards} exists to guard against elsewhere in this domain.
     */
    public static double of(double[] x, double[] y) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (x.length != y.length) {
            throw new IllegalArgumentException(
                    "x and y must have the same length, was x=%d, y=%d".formatted(x.length, y.length));
        }
        int n = x.length;
        if (n < 2) {
            throw new IllegalArgumentException("at least two values are required, was " + n);
        }

        // Two-pass centered sums rather than the raw n*Sxy - Sx*Sy form. Two reasons, both
        // load-bearing here. First, the raw form's variance term scales with the data, so
        // comparing it against an absolute epsilon rejects legitimate small-magnitude input:
        // x = y = (1e-5, 2e-5, 3e-5) has true r = 1 but yields a variance term of 6e-10,
        // which an absolute 1e-9 floor would read as "no variance". Cophenetic correlation is
        // fed cosine distances that get this small when the corpus holds near-duplicate
        // abstracts, so the primary ranking signal would fail closed on legitimate
        // input. Second, the raw form subtracts two large nearly-equal quantities and loses
        // precision to cancellation; centering first avoids that.
        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < n; i++) {
            NumericGuards.requireFinite(x[i], "x[%d]".formatted(i));
            NumericGuards.requireFinite(y[i], "y[%d]".formatted(i));
            meanX += x[i];
            meanY += y[i];
        }
        meanX /= n;
        meanY /= n;

        double sumOfProducts = 0.0;
        double centeredSquaresX = 0.0;
        double centeredSquaresY = 0.0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sumOfProducts += dx * dy;
            centeredSquaresX += dx * dx;
            centeredSquaresY += dy * dy;
        }

        // Zero variance is an exact algebraic condition, not an approximate one: it holds if
        // and only if every value in the array equals its mean. Testing it exactly keeps the
        // guard scale-free — no magnitude of legitimately-varying input can trip it.
        if (centeredSquaresX == 0.0 || centeredSquaresY == 0.0) {
            throw new IllegalArgumentException(
                    "Pearson correlation is undefined when x or y has zero variance");
        }

        double denominator = Math.sqrt(centeredSquaresX) * Math.sqrt(centeredSquaresY);
        NumericGuards.requireFinite(denominator, "denominator");

        // Guard the quotient itself, not just its operands: a denormal denominator can still
        // overflow a finite numerator to infinity, and the house rule is that no derived
        // double escapes this package unchecked (the NumericGuards contract).
        double correlation = sumOfProducts / denominator;
        NumericGuards.requireFinite(correlation, "correlation");
        return correlation;
    }
}
