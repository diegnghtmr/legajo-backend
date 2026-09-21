package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.preprocess.TextPreprocessor;
import co.edu.uniquindio.legajo.similarity.AlgorithmTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import co.edu.uniquindio.legajo.similarity.SimilarityContext;
import co.edu.uniquindio.legajo.similarity.SimilarityInput;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusIndex;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestration behind the four similarity endpoints of TRD §6.6: {@code
 * POST /similarity/compare} (multi-algorithm, one pair), {@code POST /similarity/matrix}
 * (m×m, one algorithm), {@code GET /similarity/{algorithmId}/trace} (one algorithm, one
 * pair), and {@code GET /similarity/algorithms} (the catalogue). Pure orchestration — no
 * Spring, no HTTP, no DTO/JSON annotations.
 *
 * <p><b>Request-keyed caching (task A5).</b> {@code compare} and {@code matrix} look up a
 * {@link RequestCache} keyed by {@link SimilarityCacheKey} {@code (algorithmId,
 * documentIdA, documentIdB)}, directional, before calling {@link
 * co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm#compute}; a miss computes and
 * stores the result, a hit returns the exact same {@link SimilarityResult} — including its
 * {@code computedNanos}, measured once inside {@code compute()} and never re-measured on a
 * hit. {@code trace} is never cached (the TRD's compare sequence diagram only covers
 * compare/matrix). This service depends only on the {@link RequestCache} port, never on a
 * concrete Caffeine implementation, so it stays framework-free.
 *
 * <p><b>Two embedding caches, resolved lazily.</b> {@code embedding-local} and {@code
 * embedding-api} each read a different {@link EmbeddingRepository} (the two beans
 * {@code DomainConfiguration} registers, A1); each cache is only loaded when the requested
 * algorithm set actually needs it, and only once per call, not once per document pair.
 *
 * <p><b>{@code tfidf-cosine}'s corpus-wide index.</b> Built from every corpus document's
 * preprocessed tokens, never just the compared/selected documents (TRD §6.3: "N = tamaño
 * del corpus = n = |corpus|"), and only when {@code tfidf-cosine} is among the requested
 * algorithms.
 */
public final class SimilarityService {

    private final CorpusRepository corpusRepository;
    private final SimilarityAlgorithmRegistry registry;
    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final RequestCache<SimilarityCacheKey, SimilarityResult> cache;
    private final TextPreprocessor textPreprocessor = new TextPreprocessor();

    public SimilarityService(CorpusRepository corpusRepository, SimilarityAlgorithmRegistry registry,
            EmbeddingRepository localEmbeddingRepository, EmbeddingRepository apiEmbeddingRepository,
            RequestCache<SimilarityCacheKey, SimilarityResult> cache) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /** {@code GET /similarity/algorithms}: the catalogue, in registration order. */
    public List<AlgorithmSummary> catalogue() {
        return registry.all().stream()
                .map(algorithm -> new AlgorithmSummary(algorithm.id(), algorithm.displayName(), algorithm.kind()))
                .toList();
    }

    /**
     * {@code POST /similarity/compare}: {@code algorithmIds} empty or {@code null} defaults
     * to all six (TRD §6.6, TAC-01), preserving the registry's declaration order.
     */
    public List<AlgorithmSimilarity> compare(String documentIdA, String documentIdB, List<String> algorithmIds) {
        Objects.requireNonNull(documentIdA, "documentIdA");
        Objects.requireNonNull(documentIdB, "documentIdB");

        List<SimilarityAlgorithm> algorithms = resolveAlgorithms(algorithmIds);
        Corpus corpus = corpusRepository.load();
        CorpusDocument documentA = requireDocument(corpus, documentIdA);
        CorpusDocument documentB = requireDocument(corpus, documentIdB);

        SimilarityContext context = buildContext(corpus, algorithms);
        Map<String, EmbeddingVector> localVectors = loadVectorsIfNeeded(algorithms, "embedding-local", localEmbeddingRepository);
        Map<String, EmbeddingVector> apiVectors = loadVectorsIfNeeded(algorithms, "embedding-api", apiEmbeddingRepository);

        List<AlgorithmSimilarity> results = new ArrayList<>(algorithms.size());
        for (SimilarityAlgorithm algorithm : algorithms) {
            SimilarityInput inputA = inputFor(documentA, algorithm, localVectors, apiVectors);
            SimilarityInput inputB = inputFor(documentB, algorithm, localVectors, apiVectors);
            results.add(computeCached(algorithm, inputA, inputB, context, documentIdA, documentIdB));
        }
        return List.copyOf(results);
    }

    /**
     * {@code POST /similarity/matrix}: m×m over {@code documentIds} for one algorithm, with
     * {@code m = documentIds.size()} required in {@code [3, n]} (TRD §6.6) and no duplicate
     * ids (a "selection" of m distinct documents, an author reading of "tamaño de la
     * selección" the TRD does not spell out explicitly).
     */
    public List<List<CachedSimilarityResult>> matrix(List<String> documentIds, String algorithmId) {
        Objects.requireNonNull(documentIds, "documentIds");
        Objects.requireNonNull(algorithmId, "algorithmId");

        Corpus corpus = corpusRepository.load();
        int n = corpus.documents().size();
        int m = documentIds.size();
        if (m < 3 || m > n) {
            throw new InvalidRequestException(
                    "matrix selection size must be in [3, %d] (n=%d), was %d".formatted(n, n, m));
        }
        if (new HashSet<>(documentIds).size() != m) {
            throw new InvalidRequestException("matrix selection must not contain duplicate document ids");
        }

        SimilarityAlgorithm algorithm = requireAlgorithm(algorithmId);
        List<CorpusDocument> documents = documentIds.stream().map(id -> requireDocument(corpus, id)).toList();
        SimilarityContext context = buildContext(corpus, List.of(algorithm));
        Map<String, EmbeddingVector> localVectors =
                loadVectorsIfNeeded(List.of(algorithm), "embedding-local", localEmbeddingRepository);
        Map<String, EmbeddingVector> apiVectors =
                loadVectorsIfNeeded(List.of(algorithm), "embedding-api", apiEmbeddingRepository);

        List<List<CachedSimilarityResult>> rows = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            List<CachedSimilarityResult> row = new ArrayList<>(m);
            SimilarityInput inputI = inputFor(documents.get(i), algorithm, localVectors, apiVectors);
            String documentIdI = documents.get(i).id();
            for (int j = 0; j < m; j++) {
                SimilarityInput inputJ = inputFor(documents.get(j), algorithm, localVectors, apiVectors);
                String documentIdJ = documents.get(j).id();
                AlgorithmSimilarity cell = computeCached(algorithm, inputI, inputJ, context, documentIdI, documentIdJ);
                row.add(new CachedSimilarityResult(cell.result(), cell.cached()));
            }
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }

    /**
     * Looks up {@link SimilarityCacheKey}{@code (algorithm.id(), documentIdA, documentIdB)}
     * before computing (task A5, TRD §9): a hit returns the exact same {@link
     * SimilarityResult} a fresh computation produced, {@code computedNanos} included; a miss
     * computes via {@link SimilarityAlgorithm#compute} and stores the result before
     * returning it.
     */
    private AlgorithmSimilarity computeCached(SimilarityAlgorithm algorithm, SimilarityInput inputA,
            SimilarityInput inputB, SimilarityContext context, String documentIdA, String documentIdB) {
        SimilarityCacheKey key = new SimilarityCacheKey(algorithm.id(), documentIdA, documentIdB);
        Optional<SimilarityResult> hit = cache.get(key);
        if (hit.isPresent()) {
            return new AlgorithmSimilarity(algorithm.id(), hit.get(), true);
        }
        SimilarityResult result = algorithm.compute(inputA, inputB, context);
        cache.put(key, result);
        return new AlgorithmSimilarity(algorithm.id(), result, false);
    }

    /** {@code GET /similarity/{algorithmId}/trace}: the complete trace, no truncation. */
    public Optional<AlgorithmTrace> trace(String algorithmId, String documentIdA, String documentIdB) {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(documentIdA, "documentIdA");
        Objects.requireNonNull(documentIdB, "documentIdB");

        SimilarityAlgorithm algorithm = requireAlgorithm(algorithmId);
        Corpus corpus = corpusRepository.load();
        CorpusDocument documentA = requireDocument(corpus, documentIdA);
        CorpusDocument documentB = requireDocument(corpus, documentIdB);

        SimilarityContext context = buildContext(corpus, List.of(algorithm));
        Map<String, EmbeddingVector> localVectors =
                loadVectorsIfNeeded(List.of(algorithm), "embedding-local", localEmbeddingRepository);
        Map<String, EmbeddingVector> apiVectors =
                loadVectorsIfNeeded(List.of(algorithm), "embedding-api", apiEmbeddingRepository);

        SimilarityInput inputA = inputFor(documentA, algorithm, localVectors, apiVectors);
        SimilarityInput inputB = inputFor(documentB, algorithm, localVectors, apiVectors);
        return algorithm.trace(inputA, inputB, context);
    }

    private List<SimilarityAlgorithm> resolveAlgorithms(List<String> algorithmIds) {
        if (algorithmIds == null || algorithmIds.isEmpty()) {
            return registry.all();
        }
        return algorithmIds.stream().map(this::requireAlgorithm).toList();
    }

    /**
     * Looks the algorithm up via {@link SimilarityAlgorithmRegistry#find(String)} rather than
     * {@link SimilarityAlgorithmRegistry#require(String)} so this boundary never has to catch
     * the domain's own {@link java.util.NoSuchElementException} (task A3b): catching a broad
     * JDK exception type around a call risks swallowing one thrown by an unrelated bug in the
     * same try block, which is the exact mis-classification this task removes from the REST
     * handler. Absence is translated directly into {@link ResourceNotFoundException} instead.
     */
    private SimilarityAlgorithm requireAlgorithm(String algorithmId) {
        return registry.find(algorithmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "no similarity algorithm registered with id: " + algorithmId));
    }

    private CorpusDocument requireDocument(Corpus corpus, String id) {
        return corpus.documents().stream()
                .filter(document -> document.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("no corpus document with id: " + id));
    }

    private SimilarityContext buildContext(Corpus corpus, List<SimilarityAlgorithm> algorithms) {
        boolean needsTfIdf = algorithms.stream().anyMatch(a -> a instanceof TfIdfCosine);
        if (!needsTfIdf) {
            return SimilarityContext.EMPTY;
        }
        List<List<String>> corpusTokenStreams = corpus.documents().stream()
                .map(document -> textPreprocessor.preprocess(document.abstractText()).tokens())
                .toList();
        return SimilarityContext.withTfIdfIndex(TfIdfCorpusIndex.from(corpusTokenStreams));
    }

    private Map<String, EmbeddingVector> loadVectorsIfNeeded(List<SimilarityAlgorithm> algorithms,
            String algorithmId, EmbeddingRepository repository) {
        boolean needed = algorithms.stream().anyMatch(a -> a.id().equals(algorithmId));
        if (!needed) {
            return Map.of();
        }
        Map<String, EmbeddingVector> byDocumentId = new HashMap<>();
        for (EmbeddingVector vector : repository.load().vectors()) {
            byDocumentId.put(vector.documentId(), vector);
        }
        return byDocumentId;
    }

    private SimilarityInput inputFor(CorpusDocument document, SimilarityAlgorithm algorithm,
            Map<String, EmbeddingVector> localVectors, Map<String, EmbeddingVector> apiVectors) {
        List<String> tokens = textPreprocessor.preprocess(document.abstractText()).tokens();
        EmbeddingVector vector = switch (algorithm.id()) {
            case "embedding-local" -> requireVector(localVectors, document.id(), "embedding-local");
            case "embedding-api" -> requireVector(apiVectors, document.id(), "embedding-api");
            default -> null;
        };
        return new SimilarityInput(document.abstractText(), tokens, vector);
    }

    private EmbeddingVector requireVector(Map<String, EmbeddingVector> vectors, String documentId, String cacheLabel) {
        EmbeddingVector vector = vectors.get(documentId);
        if (vector == null) {
            // The id already passed the corpus lookup, so a missing vector is an inconsistency
            // in the server's own caches (bound to corpusSha256 and verified at startup), not a
            // resource the client asked for wrongly: a server fault, never a 404.
            throw new IllegalStateException(
                    "no %s embedding cached for document id: %s".formatted(cacheLabel, documentId));
        }
        return vector;
    }
}
