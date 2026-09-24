package co.edu.uniquindio.legajo.clustering;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

/**
 * The four Lance-Williams coefficients that one {@link LinkageCriterion}
 * contributes for a given triple of cluster sizes:
 *
 * <pre>d(i∪j, k) = alphaI*d(i,k) + alphaJ*d(j,k) + beta*d(i,j) + gamma*|d(i,k)-d(j,k)|</pre>
 *
 * <p>A plain, validated value type — the four criteria classes are the only producers, and
 * {@link LanceWilliamsEngine} is the only consumer: each criterion is its own class
 * that only contributes its coefficients; the engine alone shares the merge loop. Every
 * coefficient is checked finite in the compact constructor, following
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
