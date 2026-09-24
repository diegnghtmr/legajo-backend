package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.application.cache.RequestCache;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.clustering.AverageLinkage;
import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LanceWilliamsEngine;
import co.edu.uniquindio.legajo.clustering.LeafOrder;
import co.edu.uniquindio.legajo.clustering.LinkageCriterion;
import co.edu.uniquindio.legajo.clustering.LinkageCut;
import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.clustering.WardLinkage;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.evaluation.CopheneticCorrelation;
import co.edu.uniquindio.legajo.evaluation.DaviesBouldin;
import co.edu.uniquindio.legajo.evaluation.MeanSilhouette;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.preprocess.TextPreprocessor;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import co.edu.uniquindio.legajo.similarity.Representation;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusIndex;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusVectors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Orchestration behind the three clustering endpoints: {@code POST /clustering}
 * (all four linkages' matrices + evaluation, one shared representation), {@code
 * POST /clustering/evaluation} (the same computation, evaluation block only), and {@code
 * POST /clustering/cut} (the one endpoint with a free {@code k}). Pure orchestration — no
 * Spring, no HTTP, no DTO/JSON annotations.
 *
 * <p><b>The fixed-cut rule is structural.</b> {@link #fixedKsFor(int)} always
 * derives {@code k ∈ {2,3,4,5} ∩ [2, n-1]} from {@code n} alone; no method on this class
 * accepts a caller-supplied set of {@code k}s for the evaluation block, so no future
 * controller can wire a {@code ks} parameter into it. {@link #cut} is the only method that
 * takes a caller-supplied {@code k}, matching "the only endpoint accepting a free cut".
 *
 * <p><b>Cophenetic correlation always against base D, never Ward's D_w.</b> Ward's engine
 * input is {@code distances.wardBase()} ({@code D_w = 2·D}), but
 * {@link CopheneticCorrelation#of} and {@link MeanSilhouette#of} are always evaluated
 * against the one shared reference {@code distances} (base D) so all four linkages stay
 * comparable on the same axis — Pearson correlation is invariant to Ward's
 * positive 2x rescaling, so this is equivalent to correlating against D_w, never a
 * different number.
 *
 * <p><b>Request-keyed caching.</b> {@link #run}, {@link #evaluateOnly}
 * (which delegates to {@code run}) and {@link #cut} all share one {@link RequestCache} keyed
 * by {@link ClusteringCacheKey} {@code (representation, linkageId)}: the first of the three
 * to ask for a given linkage computes it and stores the full {@link LinkageRunResult}; every
 * later call for that same key, from any of the three endpoints, reuses it instead of
 * recomputing — {@code cut} reconstructs a {@link LinkageMatrix} from the cached rows and
 * cuts that tree, still validating its own {@code k} range on every call. This never lets a
 * request depend on a *different* previous request's value under the statelessness rule:
 * the cached value for a key is always exactly what a fresh computation of that same key
 * would produce.
 */
public final class ClusteringService {

    private static final List<Integer> CANDIDATE_KS = List.of(2, 3, 4, 5);

    private final CorpusRepository corpusRepository;
    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final RequestCache<ClusteringCacheKey, LinkageRunResult> cache;
    private final TextPreprocessor textPreprocessor = new TextPreprocessor();
    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();

    public ClusteringService(CorpusRepository corpusRepository, EmbeddingRepository localEmbeddingRepository,
            EmbeddingRepository apiEmbeddingRepository, RequestCache<ClusteringCacheKey, LinkageRunResult> cache) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /**
     * {@code POST /clustering}: {@code representation} defaults to
     * {@link Representation#DEFAULT} (tfidf-cosine) and {@code linkageIds} defaults to all
     * four, in the fixed declaration order (single, complete, average, ward).
     */
    public List<LinkageRunResult> run(Representation representation, List<String> linkageIds) {
        Representation effectiveRepresentation = representation == null ? Representation.DEFAULT : representation;
        List<LinkageCriterion> criteria = resolveLinkages(linkageIds);

        // Corpus/vectors/distances are representation-wide, not per-linkage; loaded lazily,
        // at most once, and only if at least one requested linkage is actually a cache miss.
        List<List<Double>> vectors = null;
        DistanceMatrix distances = null;
        List<Integer> fixedKs = null;
        List<String> documentIds = null;

        List<LinkageRunResult> results = new ArrayList<>(criteria.size());
        for (LinkageCriterion criterion : criteria) {
            ClusteringCacheKey key = new ClusteringCacheKey(effectiveRepresentation, criterion.id());
            Optional<LinkageRunResult> hit = cache.get(key);
            if (hit.isPresent()) {
                results.add(hit.get());
                continue;
            }
            if (distances == null) {
                Corpus corpus = corpusRepository.load();
                vectors = vectorsFor(effectiveRepresentation, corpus);
                distances = DistanceMatrix.cosineDistance(vectors);
                fixedKs = fixedKsFor(distances.size());
                documentIds = documentIdsOf(corpus);
            }
            LinkageRunResult result = computeLinkage(criterion, distances, vectors, fixedKs, documentIds);
            cache.put(key, result);
            results.add(result);
        }
        return List.copyOf(results);
    }

    /** {@code POST /clustering/evaluation}: same computation as {@link #run}, evaluation only. */
    public List<LinkageEvaluationOnly> evaluateOnly(Representation representation, List<String> linkageIds) {
        return run(representation, linkageIds).stream()
                .map(r -> new LinkageEvaluationOnly(r.linkageId(), r.linkageDisplayName(), r.evaluation()))
                .toList();
    }

    /**
     * {@code POST /clustering/cut}: the only endpoint accepting a free {@code k}, required
     * in {@code [2, n-1]}. {@code representation} defaults to
     * {@link Representation#DEFAULT}, mirroring {@link #run} — {@code representation} is the
     * same shared enum with the same documented default for every clustering endpoint, so
     * this is a direct, not an invented, extension.
     *
     * <p><b>Client-supplied {@code k} out of range answers 400, not 500.</b> This method used
     * to pass {@code k} straight to {@link LinkageCut#cut}, which itself enforces
     * {@code [2, n-1]} but does so with a <em>raw</em> {@link IllegalArgumentException} — a
     * raw IAE reaching the REST boundary is (correctly) treated as a server bug and answers
     * 500. A client-supplied {@code k} outside range is a
     * request-validation failure, not a server fault, so this application boundary now
     * checks the range itself and throws the dedicated {@link InvalidRequestException}
     * subtype (400) *before* the domain ever sees the bad value.
     * {@link LinkageCut#cut}'s own check is kept as a defense-in-depth invariant guard for
     * any other caller, not removed.
     */
    public ClusteringCutResult cut(Representation representation, String linkageId, int k) {
        Representation effectiveRepresentation = representation == null ? Representation.DEFAULT : representation;
        LinkageCriterion criterion = resolveLinkage(linkageId);

        ClusteringCacheKey key = new ClusteringCacheKey(effectiveRepresentation, criterion.id());
        LinkageRunResult cachedResult = cache.get(key).orElseGet(() -> {
            Corpus corpus = corpusRepository.load();
            List<List<Double>> vectors = vectorsFor(effectiveRepresentation, corpus);
            DistanceMatrix distances = DistanceMatrix.cosineDistance(vectors);
            List<Integer> fixedKs = fixedKsFor(distances.size());
            List<String> documentIds = documentIdsOf(corpus);
            LinkageRunResult result = computeLinkage(criterion, distances, vectors, fixedKs, documentIds);
            cache.put(key, result);
            return result;
        });

        // Rebuilt from the cached rows rather than re-agglomerating: LinkageMatrix's compact
        // constructor only re-validates the monotonicity invariant (cheap), it never
        // recomputes anything.
        LinkageMatrix linkage = new LinkageMatrix(cachedResult.rows());
        int n = linkage.size() + 1;
        if (k < 2 || k > n - 1) {
            throw new InvalidRequestException(ProblemType.INVALID_CUT,
                    "k must be in [2, n-1] (n=%d), was %d".formatted(n, k));
        }
        return new ClusteringCutResult(LinkageCut.cut(linkage, k), cachedResult.documentIds());
    }

    private LinkageRunResult computeLinkage(LinkageCriterion criterion, DistanceMatrix distances,
            List<List<Double>> vectors, List<Integer> fixedKs, List<String> documentIds) {
        LinkageMatrix linkage = engine.agglomerate(engineInputFor(criterion, distances), criterion);
        List<Integer> leafOrder = LeafOrder.of(linkage);
        double cophenetic = CopheneticCorrelation.of(linkage, distances);

        Map<Integer, Double> silhouetteByK = new LinkedHashMap<>();
        Map<Integer, OptionalDouble> daviesBouldinByK = new LinkedHashMap<>();
        for (int k : fixedKs) {
            ClusterAssignment assignment = LinkageCut.cut(linkage, k);
            silhouetteByK.put(k, MeanSilhouette.of(assignment, distances));
            daviesBouldinByK.put(k, DaviesBouldin.of(assignment, vectors));
        }

        ClusteringEvaluationBlock evaluation =
                new ClusteringEvaluationBlock(cophenetic, silhouetteByK, daviesBouldinByK);
        return new LinkageRunResult(
                criterion.id(), criterion.displayName(), linkage.rows(), leafOrder, documentIds, evaluation);
    }

    /** The document behind each observation index, in {@code corpus.json} order —
     * the same order {@link #vectorsFor} builds the distance matrix from, so index i means
     * the same thing on both sides without recomputing it separately. */
    private List<String> documentIdsOf(Corpus corpus) {
        return corpus.documents().stream().map(CorpusDocument::id).toList();
    }

    /** Ward always agglomerates over {@code D_w = 2·D}; the other three over base D. */
    private DistanceMatrix engineInputFor(LinkageCriterion criterion, DistanceMatrix distances) {
        return criterion instanceof WardLinkage ? distances.wardBase() : distances;
    }

    /** The fixed cut set: {@code {2,3,4,5} ∩ [2, n-1]}, derived from {@code n} alone. */
    private List<Integer> fixedKsFor(int n) {
        List<Integer> ks = new ArrayList<>();
        for (int candidate : CANDIDATE_KS) {
            if (candidate >= 2 && candidate <= n - 1) {
                ks.add(candidate);
            }
        }
        return List.copyOf(ks);
    }

    private List<List<Double>> vectorsFor(Representation representation, Corpus corpus) {
        return switch (representation) {
            case TFIDF_COSINE -> tfIdfVectors(corpus);
            case EMBEDDING_LOCAL -> embeddingVectors(corpus, localEmbeddingRepository, "embedding-local");
            case EMBEDDING_API -> embeddingVectors(corpus, apiEmbeddingRepository, "embedding-api");
        };
    }

    private List<List<Double>> tfIdfVectors(Corpus corpus) {
        List<List<String>> tokenStreams = corpus.documents().stream()
                .map(document -> textPreprocessor.preprocess(document.abstractText()).tokens())
                .toList();
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(tokenStreams);
        return TfIdfCorpusVectors.vectorsOf(tokenStreams, index);
    }

    private List<List<Double>> embeddingVectors(Corpus corpus, EmbeddingRepository repository, String cacheLabel) {
        EmbeddingCache cache = repository.load();
        List<List<Double>> vectors = new ArrayList<>(corpus.documents().size());
        for (CorpusDocument document : corpus.documents()) {
            // The caches are bound to corpusSha256 and verified at startup, so a corpus document
            // with no cached vector means the server's own data is inconsistent. The client asked
            // for a valid representation and supplied nothing wrong, so this is a server fault
            // (500), never a "not found" that would blame the client.
            EmbeddingVector vector = cache.find(document.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "no %s embedding cached for document id: %s".formatted(cacheLabel, document.id())));
            vectors.add(vector.values());
        }
        return List.copyOf(vectors);
    }

    private List<LinkageCriterion> resolveLinkages(List<String> linkageIds) {
        if (linkageIds == null || linkageIds.isEmpty()) {
            return List.of(new SingleLinkage(), new CompleteLinkage(), new AverageLinkage(), new WardLinkage());
        }
        return linkageIds.stream().map(this::resolveLinkage).toList();
    }

    /**
     * No registry exists for the four fixed {@link LinkageCriterion}s (unlike similarity's
     * {@code SimilarityAlgorithmRegistry}); an exhaustive id switch mirrors
     * {@code ClusteringRanking#declarationOrder}'s existing convention for this same closed set.
     *
     * <p><b>An unknown linkage id answers 400, not 404.</b> {@code linkage}/{@code linkages}
     * is never a path
     * segment on any {@code /clustering*} endpoint — it always arrives in the request body —
     * so, unlike {@code SimilarityService.requireAlgorithm}, there is no ambiguous caller to
     * defer to and this can throw the final classification directly
     * ({@code urn:legajo:problem:unknown-linkage}). Before this an unknown linkage id was
     * {@link co.edu.uniquindio.legajo.application.error.ResourceNotFoundException} (404); 404
     * is fixed to path-identified resources only, and a linkage id sent in a body is a
     * request-validation failure, not a missing resource.
     */
    private LinkageCriterion resolveLinkage(String id) {
        return switch (id) {
            case "single" -> new SingleLinkage();
            case "complete" -> new CompleteLinkage();
            case "average" -> new AverageLinkage();
            case "ward" -> new WardLinkage();
            default -> throw new InvalidRequestException(ProblemType.UNKNOWN_LINKAGE, "unknown linkage id: " + id);
        };
    }
}
