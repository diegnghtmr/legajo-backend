/**
 * SLO (service-level objective) benchmarks over the real reference corpus (n = 20): the
 * classic-pairwise benchmark (all C(n,2) classic pairwise comparisons per algorithm,
 * similarity cache off) and the clustering benchmark (single, complete, average and Ward
 * agglomeration from a precomputed distance matrix). Domain algorithms are called directly —
 * bypassing the application-layer service and its cache entirely, not merely wrapping it with
 * a no-op cache, since these benchmarks must depend only on {@code :domain}.
 */
package co.edu.uniquindio.legajo.benchmarks.slo;
