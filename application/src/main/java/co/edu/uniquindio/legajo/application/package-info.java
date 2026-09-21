/**
 * Use cases orchestrating the domain: the three ingest use cases ({@code ingest}), plus the
 * REST feature's four orchestration services (TRD §6.6) — {@code CorpusService} ({@code
 * corpus}), {@code SimilarityService} ({@code similarity}), {@code ClusteringService}
 * ({@code clustering}), and {@code EmbeddingsService} ({@code embedding}). Every service is
 * pure orchestration over {@code domain}: no Spring, no HTTP, no DTO/JSON annotations.
 */
@NullMarked
package co.edu.uniquindio.legajo.application;

import org.jspecify.annotations.NullMarked;
