package co.edu.uniquindio.legajo.application.embedding;

import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;

import java.util.Objects;

/**
 * Orchestration behind {@code GET /embeddings/status} (TRD §6.6). Pure orchestration — no
 * Spring, no HTTP, no DTO/JSON annotations.
 *
 * <p><b>Flagged ambiguity: one embedding family or both?</b> The TRD lists this endpoint's
 * fields in the singular ("provider, model, dimension...") and never says whether it reports
 * on one embedding cache or on both {@code embedding-local} and {@code embedding-api}
 * independently — the two families each have their own cache file, provider, and model, and
 * both matter for the demo (TAC-13). Rather than silently pick one shape, {@link #status}
 * computes the status of a single {@link EmbeddingRepository} the caller passes in; the REST
 * layer (A3/A4) can call it once for a single-family response or twice (with each of the
 * beans {@code DomainConfiguration} registers) for a per-family response — either way, this
 * method does not need to change.
 *
 * <p>{@code device} and {@code mode} are not derived from any stored data (unlike {@code
 * provider}/{@code model}/{@code dimension}, which come from the loaded {@link
 * EmbeddingCache}): {@code device} is a deployment fact (TRD §14.2: the default profile is
 * CPU-only) with no corresponding {@code application.yml} key today, and {@code mode} is
 * {@code legajo.embedding-provider}'s resolved value (A1's {@code LegajoProperties}). Both
 * are accepted as parameters so this service stays framework-free and does not itself decide
 * infrastructure/deployment facts.
 */
public final class EmbeddingsService {

    private final CorpusRepository corpusRepository;

    public EmbeddingsService(CorpusRepository corpusRepository) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
    }

    public EmbeddingStatus status(EmbeddingRepository embeddingRepository, String device, EmbeddingProviderMode mode) {
        Objects.requireNonNull(embeddingRepository, "embeddingRepository");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(mode, "mode");

        String corpusSha256 = corpusRepository.load().corpusSha256();
        EmbeddingCache cache = embeddingRepository.load();
        // TRD does not cover an empty cache's provider (no vector to read one from); "unknown"
        // is a fail-soft placeholder, flagged rather than silently guessed as a real provider id.
        String provider = cache.vectors().isEmpty() ? "unknown" : cache.vectors().get(0).provider();
        boolean matchesCorpus = cache.corpusSha256().equals(corpusSha256);

        return new EmbeddingStatus(provider, cache.model(), cache.dimension(), device, mode, cache.corpusSha256(),
                matchesCorpus);
    }
}
