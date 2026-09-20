package co.edu.uniquindio.legajo.clustering;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

/**
 * The four Lance-Williams coefficients (TRD §6.4) that one {@link LinkageCriterion}
 * contributes for a given triple of cluster sizes:
 *
 * <pre>d(i∪j, k) = alphaI*d(i,k) + alphaJ*d(j,k) + beta*d(i,j) + gamma*|d(i,k)-d(j,k)|</pre>
 *
 * <p>A plain, validated value type — the four criteria classes are the only producers, and
 * {@link LanceWilliamsEngine} (R3) is the only consumer, matching the TRD's "cada criterio
 * es su propia clase ... y aporta sus coeficientes; el motor solo comparte el bucle de
 * fusión". Every coefficient is checked finite in the compact constructor, following
 * {@link NumericGuards}'s house rule (see its own Javadoc) that a derived {@code double}
 * must never carry a silent {@code NaN} or infinity into a merge-loop computation.
 */
public record LanceWilliamsCoefficients(double alphaI, double alphaJ, double beta, double gamma) {

    public LanceWilliamsCoefficients {
        NumericGuards.requireFinite(alphaI, "alphaI");
        NumericGuards.requireFinite(alphaJ, "alphaJ");
        NumericGuards.requireFinite(beta, "beta");
        NumericGuards.requireFinite(gamma, "gamma");
    }
}
