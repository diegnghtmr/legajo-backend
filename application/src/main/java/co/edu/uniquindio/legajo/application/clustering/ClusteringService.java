package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
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
import java.util.OptionalDouble;

/**
 * Orchestration behind the three clustering endpoints of TRD §6.6: {@code POST /clustering}
 * (all four linkages' matrices + evaluation, one shared representation), {@code
 * POST /clustering/evaluation} (the same computation, evaluation block only), and {@code
 * POST /clustering/cut} (the one endpoint with a free {@code k}). Pure orchestration — no
 * Spring, no HTTP, no DTO/JSON annotations.
 *
 * <p><b>The fixed-cut rule is structural (TAC-04).</b> {@link #fixedKsFor(int)} always
 * derives {@code k ∈ {2,3,4,5} ∩ [2, n-1]} from {@code n} alone; no method on this class
 * accepts a caller-supplied set of {@code k}s for the evaluation block, so no future
 * controller can wire a {@code ks} parameter into it. {@link #cut} is the only method that
 * takes a caller-supplied {@code k}, matching "the only endpoint accepting a free cut".
 *
 * <p><b>Cophenetic correlation always against base D, never Ward's D_w.</b> Ward's engine
 * input is {@code distances.wardBase()} (TRD §6.4: {@code D_w = 2·D}), but
 * {@link CopheneticCorrelation#of} and {@link MeanSilhouette#of} are always evaluated
 * against the one shared reference {@code distances} (base D) so all four linkages stay
 * comparable on the same axis (TRD §6.4/§6.5) — Pearson correlation is invariant to Ward's
 * positive 2x rescaling, so this is equivalent to correlating against D_w, never a
 * different number.
 */
public final class ClusteringService {

    private static final List<Integer> CANDIDATE_KS = List.of(2, 3, 4, 5);

    private final CorpusRepository corpusRepository;
    private final EmbeddingRepository localEmbeddingRepository;
    private final EmbeddingRepository apiEmbeddingRepository;
    private final TextPreprocessor textPreprocessor = new TextPreprocessor();
    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();

    public ClusteringService(CorpusRepository corpusRepository, EmbeddingRepository localEmbeddingRepository,
            EmbeddingRepository apiEmbeddingRepository) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
        this.localEmbeddingRepository = Objects.requireNonNull(localEmbeddingRepository, "localEmbeddingRepository");
        this.apiEmbeddingRepository = Objects.requireNonNull(apiEmbeddingRepository, "apiEmbeddingRepository");
    }

    /**
     * {@code POST /clustering}: {@code representation} defaults to
     * {@link Representation#DEFAULT} (tfidf-cosine) and {@code linkageIds} defaults to all
     * four, in the fixed declaration order (single, complete, average, ward) — TRD §6.6.
     */
    public List<LinkageRunResult> run(Representation representation, List<String> linkageIds) {
        Representation effectiveRepresentation = representation == null ? Representation.DEFAULT : representation;
        List<LinkageCriterion> criteria = resolveLinkages(linkageIds);

        Corpus corpus = corpusRepository.load();
        List<List<Double>> vectors = vectorsFor(effectiveRepresentation, corpus);
        DistanceMatrix distances = DistanceMatrix.cosineDistance(vectors);
        List<Integer> fixedKs = fixedKsFor(distances.size());

        List<LinkageRunResult> results = new ArrayList<>(criteria.size());
        for (LinkageCriterion criterion : criteria) {
            results.add(computeLinkage(criterion, distances, vectors, fixedKs));
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
     * in {@code [2, n-1]} (TRD §6.6). {@code representation} defaults to
     * {@link Representation#DEFAULT}, mirroring {@link #run} — the TRD does not restate this
     * default specifically for {@code cut}, but {@code representation} is the same shared
     * enum with the same documented default for every clustering endpoint, so this is a
     * direct, not an invented, extension.
     *
     * <p><b>A4 fix (feature doc {@code rest-api.md}, advisory
     * {@code R3-narrowed-handler-unmigrated-throw-sites}).</b> This method used to pass
     * {@code k} straight to {@link LinkageCut#cut}, which itself enforces {@code [2, n-1]}
     * but does so with a <em>raw</em> {@link IllegalArgumentException} — since A3b's
     * error-classification fix, a raw IAE reaching the REST boundary is (correctly) treated
     * as a server bug and answers 500. A client-supplied {@code k} outside range is a
     * request-validation failure, not a server fault, so this application boundary now
     * checks the range itself and throws the dedicated {@link InvalidRequestException}
     * subtype (400) *before* the domain ever sees the bad value.
     * {@link LinkageCut#cut}'s own check is kept as a defense-in-depth invariant guard for
     * any other caller, not removed.
     */
    public ClusterAssignment cut(Representation representation, String linkageId, int k) {
        Representation effectiveRepresentation = representation == null ? Representation.DEFAULT : representation;
        LinkageCriterion criterion = resolveLinkage(linkageId);

        Corpus corpus = corpusRepository.load();
        List<List<Double>> vectors = vectorsFor(effectiveRepresentation, corpus);
        DistanceMatrix distances = DistanceMatrix.cosineDistance(vectors);
        LinkageMatrix linkage = engine.agglomerate(engineInputFor(criterion, distances), criterion);

        int n = linkage.size() + 1;
        if (k < 2 || k > n - 1) {
            throw new InvalidRequestException("k must be in [2, n-1] (n=%d), was %d".formatted(n, k));
        }
        return LinkageCut.cut(linkage, k);
    }

    private LinkageRunResult computeLinkage(LinkageCriterion criterion, DistanceMatrix distances,
            List<List<Double>> vectors, List<Integer> fixedKs) {
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
        return new LinkageRunResult(criterion.id(), criterion.displayName(), linkage.rows(), leafOrder, evaluation);
    }

    /** Ward always agglomerates over {@code D_w = 2·D} (TRD §6.4); the other three over base D. */
    private DistanceMatrix engineInputFor(LinkageCriterion criterion, DistanceMatrix distances) {
        return criterion instanceof WardLinkage ? distances.wardBase() : distances;
    }

    /** TAC-04's fixed cut set: {@code {2,3,4,5} ∩ [2, n-1]}, derived from {@code n} alone. */
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

    /** No registry exists for the four fixed {@link LinkageCriterion}s (unlike similarity's
     * {@code SimilarityAlgorithmRegistry}); an exhaustive id switch mirrors
     * {@code ClusteringRanking#declarationOrder}'s existing convention for this same closed set. */
    private LinkageCriterion resolveLinkage(String id) {
        return switch (id) {
            case "single" -> new SingleLinkage();
            case "complete" -> new CompleteLinkage();
            case "average" -> new AverageLinkage();
            case "ward" -> new WardLinkage();
            default -> throw new ResourceNotFoundException("unknown linkage id: " + id);
        };
    }
}
