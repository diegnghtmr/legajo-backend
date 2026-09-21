package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Live implementation of {@link EmbeddingRepository} for {@code embedding-api} (TRD §6.3,
 * "Modo en vivo de {@code embedding-api} (fijado)"; feature doc task A8): with {@code
 * legajo.embedding-provider=live}, this adapter replaces {@link JsonEmbeddingRepository} as
 * the {@code apiEmbeddingRepository} bean ({@code DomainConfiguration}) and fetches each
 * document's vector from the remote model through {@link OpenAiCompatibleEmbedder} at
 * request time instead of reading the versioned {@code embeddings-openai.json} cache.
 *
 * <p><b>Same input text, same normalization, no duplicated math.</b> {@link #load()} reads
 * every document's raw post-ingestion {@code abstract} from {@link CorpusRepository} (TRD
 * §6.3, "Texto de entrada por familia") and hands it to {@link OpenAiCompatibleEmbedder#embed},
 * which already L2-renormalizes the returned vector with the shared hand-written {@link
 * EmbeddingVector#normalize} — the exact normalization {@link JsonEmbeddingRepository} reuses
 * on load, under the same unit-norm invariant (TRD §6.3). This class does not renormalize a
 * second time; {@link EmbeddingVector}'s own compact constructor already refuses a
 * non-unit-length vector, so a mistake here would fail loudly, not silently.
 *
 * <p><b>Caffeine {@code embeddings} region, no TTL (TRD §9).</b> {@link #cache} is a plain
 * per-instance Caffeine cache keyed by document id: since {@code DomainConfiguration} registers
 * this adapter as a Spring singleton bean, one instance lives for the whole process, so every
 * document is fetched from the network at most once per process — exactly TRD's requirement —
 * regardless of how many requests or how many of {@link #load()}'s callers ask for it.
 * {@link Cache#get(Object, java.util.function.Function)} makes the fetch-or-return atomic, so
 * two concurrent requests racing on the same uncached document id still only trigger one
 * network call. A failed fetch is never cached (Caffeine's contract for a throwing mapping
 * function), so the next call retries instead of getting stuck failing forever.
 *
 * <p><b>No silent fallback (TRD §6.3).</b> Any failure — a 5xx, a timeout, a rejected request,
 * a missing/blank API key — surfaces as {@link EmbeddingApiException} (already mapped to a 503
 * Problem Detail by {@code ProblemDetailExceptionHandler}) and never falls back to the
 * versioned JSON cache, which would falsely report a mode this instance is not using.
 *
 * <p><b>Deferred, never eager, client construction.</b> The underlying {@link
 * OpenAiCompatibleEmbedder} is built lazily, on the first vector request, not in this class's
 * constructor or in the {@code DomainConfiguration} bean factory method that creates this
 * instance. This is what lets {@code legajo.embedding-provider=live} be configured with a
 * blank or missing {@code SPRING_AI_OPENAI_API_KEY} (or base URL) without failing application
 * startup: the TRD requires a missing key to answer a 503 per request, not to crash the
 * server — any exception raised while configuring the client (as well as any raised while
 * calling it) is wrapped in {@link EmbeddingApiException} the same way.
 *
 * <p><b>Always matches the current corpus.</b> Unlike {@link JsonEmbeddingRepository}, this
 * adapter never fails closed on a {@code corpusSha256} mismatch: the {@link EmbeddingCache} it
 * returns is always built directly from {@link CorpusRepository#load()}'s current corpus, so
 * {@code corpusSha256} always matches by construction and {@code matchesCorpus} in {@code
 * GET /embeddings/status} is always {@code true} in live mode — there is no separate cache
 * file that could drift from the corpus.
 *
 * <p><b>Read-only.</b> {@link #save} is not supported: nothing at serve time writes an
 * embedding cache file; only the offline precompute CLI does, directly against {@link
 * JsonEmbeddingRepository}.
 */
public final class LiveApiEmbeddingRepository implements EmbeddingRepository {

    private static final String SCHEMA_VERSION = "1.0";

    private final CorpusRepository corpusRepository;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimension;
    private final Duration timeout;
    private final Cache<String, EmbeddingVector> cache = Caffeine.newBuilder().build();

    private volatile OpenAiCompatibleEmbedder embedder;

    public LiveApiEmbeddingRepository(CorpusRepository corpusRepository, String apiKey, String baseUrl,
            String model, int dimension) {
        this(corpusRepository, apiKey, baseUrl, model, dimension, null);
    }

    /** Test-only seam: a shorter timeout than {@link OpenAiCompatibleEmbedder}'s 30s default. */
    LiveApiEmbeddingRepository(CorpusRepository corpusRepository, String apiKey, String baseUrl, String model,
            int dimension, Duration timeout) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.model = Objects.requireNonNull(model, "model");
        this.dimension = dimension;
        this.timeout = timeout;
    }

    @Override
    public EmbeddingCache load() {
        Corpus corpus = corpusRepository.load();
        List<EmbeddingVector> vectors = new ArrayList<>(corpus.documents().size());
        for (CorpusDocument document : corpus.documents()) {
            vectors.add(vectorFor(document));
        }
        return new EmbeddingCache(SCHEMA_VERSION, corpus.version(), corpus.corpusSha256(), model, dimension, vectors);
    }

    @Override
    public void save(EmbeddingCache cache) {
        throw new UnsupportedOperationException(
                "LiveApiEmbeddingRepository is read-only; run the offline precompute CLI "
                        + "(:bootstrap:precomputeApiEmbeddings) to write embeddings-openai.json");
    }

    /**
     * At most one network call per {@code documentId} for the lifetime of this instance
     * (TRD §6.3). {@link Cache#get(Object, java.util.function.Function)} computes and stores
     * atomically, so a failure is never cached and a concurrent race never double-fetches.
     */
    private EmbeddingVector vectorFor(CorpusDocument document) {
        return cache.get(document.id(), id -> embedder().embed(id, document.abstractText()));
    }

    /**
     * Lazily builds the underlying client on first use, wrapping any construction failure
     * (e.g. a malformed base URL) in {@link EmbeddingApiException} exactly like a call
     * failure, so a misconfigured live mode degrades per request, never at startup.
     */
    private OpenAiCompatibleEmbedder embedder() {
        OpenAiCompatibleEmbedder existing = embedder;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (embedder == null) {
                try {
                    embedder = timeout == null
                            ? new OpenAiCompatibleEmbedder(apiKey, baseUrl, model, dimension)
                            : new OpenAiCompatibleEmbedder(apiKey, baseUrl, model, dimension, timeout);
                } catch (RuntimeException e) {
                    throw new EmbeddingApiException(
                            "Failed to configure the live embedding-api client (model=%s): %s"
                                    .formatted(model, e.getMessage()),
                            e);
                }
            }
            return embedder;
        }
    }
}
