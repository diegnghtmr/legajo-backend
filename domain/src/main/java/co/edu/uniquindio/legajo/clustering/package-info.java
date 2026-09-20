/**
 * Hierarchical clustering via the Lance-Williams update formula and its four linkages:
 * single, complete, average, and Ward (TRD §6.4). {@link DistanceMatrix} is the distance
 * base every linkage consumes: D = 1 - cos(V) derived from the run's selected
 * representation, and D_w = 2*D for Ward. The linkage criteria and the Lance-Williams
 * engine itself are follow-up feature tasks (R2/R3).
 */
@NullMarked
package co.edu.uniquindio.legajo.clustering;

import org.jspecify.annotations.NullMarked;
