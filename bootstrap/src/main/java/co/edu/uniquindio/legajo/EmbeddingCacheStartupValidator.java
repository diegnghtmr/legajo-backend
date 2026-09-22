package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;

import java.util.Objects;

/**
 * TRD §6.1/§9, TAC-13 ("corpusSha256 discrepante → error de arranque"): validates, once, every
 * embedding cache the configured {@link EmbeddingProviderMode} actually serves against the
 * corpus it was bound to at construction time — so a stale or mismatched cache stops the
 * application from starting instead of surfacing as a 500 Problem Detail on the first request
 * that happens to need it.
 *
 * <p>{@code embedding-local} is validated unconditionally: the server always serves it from
 * {@code data/embeddings-minilm.json}, regardless of {@code legajo.embedding-provider}.
 * {@code embedding-api} is validated only in {@link EmbeddingProviderMode#CACHED} — in {@link
 * EmbeddingProviderMode#LIVE}, {@code DomainConfiguration} swaps that bean for {@code
 * LiveApiEmbeddingRepository}, which fetches vectors from the remote model at request time and
 * never reads {@code data/embeddings-openai.json}; that file's staleness cannot affect a
 * running live-mode server, and TRD keeps a missing/misconfigured live client out of startup
 * entirely ("clave ausente responden 503", never a boot failure) — validating it here would
 * fail the boot for a file nothing is serving.
 *
 * <p>{@link #validate()} calls {@link EmbeddingRepository#load()} exactly the same way {@code
 * SimilarityService}/{@code ClusteringService}/{@code EmbeddingsService} do per request; a
 * {@code corpusSha256} mismatch propagates {@code JsonEmbeddingRepository.load()}'s existing
 * {@link IllegalStateException}, which names the {@code precomputeEmbeddings} Gradle task to
 * re-run, unmodified. This class has no Spring import of its own — {@code
 * DomainConfiguration} is the one place that wires it to Spring's {@code
 * SmartInitializingSingleton} startup hook, so a plain {@code IllegalStateException} thrown
 * from {@link #validate()} aborts context refresh with no framework-specific wrapping added
 * here.
 */
final class EmbeddingCacheStartupValidator {

    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final EmbeddingProviderMode embeddingProviderMode;

    EmbeddingCacheStartupValidator(EmbeddingRepository localEmbeddingRepository,
            EmbeddingRepository apiEmbeddingRepository, EmbeddingProviderMode embeddingProviderMode) {
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
        this.embeddingProviderMode = Objects.requireNonNull(embeddingProviderMode, "embeddingProviderMode");
    }

    void validate() {
        localEmbeddingRepository.load();
        if (embeddingProviderMode == EmbeddingProviderMode.CACHED) {
            apiEmbeddingRepository.load();
        }
    }
}
