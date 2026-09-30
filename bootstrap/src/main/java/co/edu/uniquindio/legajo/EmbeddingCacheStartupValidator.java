package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingCacheCoverage;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <p>Beyond the {@code corpusSha256} comparison, each served cache must hold exactly one
 * vector per corpus document id (no missing, unexpected or repeated ids); a mismatch stops the
 * boot with a message naming the precompute task. Vector dimensions are checked on load by the
 * repository itself.
 *
 * <p>Any {@code load()} failure stops the boot, not only a {@code corpusSha256} mismatch: a
 * served cache file that is missing, unreadable or malformed is as unusable as a stale one, and
 * the same fail-closed rule that applies to the mismatch applies here too.
 */
final class EmbeddingCacheStartupValidator {

    private final CorpusRepository corpusRepository;
    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final EmbeddingProviderMode embeddingProviderMode;

    EmbeddingCacheStartupValidator(CorpusRepository corpusRepository, EmbeddingRepository localEmbeddingRepository,
            EmbeddingRepository apiEmbeddingRepository, EmbeddingProviderMode embeddingProviderMode) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
        this.embeddingProviderMode = Objects.requireNonNull(embeddingProviderMode, "embeddingProviderMode");
    }

    void validate() {
        Set<String> corpusIds = corpusRepository.load().documents().stream()
                .map(CorpusDocument::id)
                .collect(Collectors.toSet());
        requireCoverage("embedding-local", localEmbeddingRepository.load(), corpusIds);
        if (embeddingProviderMode == EmbeddingProviderMode.CACHED) {
            requireCoverage("embedding-api", apiEmbeddingRepository.load(), corpusIds);
        }
    }

    private static void requireCoverage(String capability, EmbeddingCache cache, Set<String> corpusIds) {
        EmbeddingCacheCoverage.mismatch(corpusIds, cache).ifPresent(problem -> {
            throw new IllegalStateException(
                    ("%s embedding cache does not match the corpus documents: %s. "
                            + "Re-run the offline precompute command: ./gradlew :bootstrap:precomputeEmbeddings")
                            .formatted(capability, problem));
        });
    }
}
