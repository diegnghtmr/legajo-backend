package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;

import java.util.Objects;

/**
 * A mismatched {@code corpusSha256} must fail application startup, not surface as a runtime
 * error later: validates, once, every
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
 * running live-mode server, and a missing or misconfigured live client is deliberately kept
 * out of startup entirely (it answers 503 on first use, never a boot failure) — validating it
 * here would fail the boot for a file nothing is serving.
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
 *
 * <p>Any {@code load()} failure stops the boot, not only a {@code corpusSha256} mismatch: a
 * served cache file that is missing, unreadable or malformed is as unusable as a stale one, and
 * the same fail-closed rule that applies to the mismatch applies here too.
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
