package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.application.cache.FakeRequestCache;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import co.edu.uniquindio.legajo.similarity.Representation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the clustering endpoints: pure orchestration wiring
 * {@code DistanceMatrix}, the four {@code LinkageCriterion}s, {@code LanceWilliamsEngine},
 * {@code LeafOrder}, {@code CopheneticCorrelation}, {@code MeanSilhouette}, and
 * {@code DaviesBouldin} together. Uses a 5-document corpus so the fixed evaluation cuts
 * {2,3,4,5} ∩ [2, n-1] genuinely exercise the intersection (n-1=4 excludes k=5).
 */
class ClusteringServiceTest {

    private static final List<CorpusDocument> DOCS = List.of(
            doc("d01", "cats and dogs are common household pets around the world"),
            doc("d02", "dogs are loyal pets that live with families at home"),
            doc("d03", "stock markets and interest rates move the global economy"),
            doc("d04", "interest rates set by central banks affect stock markets"),
            doc("d05", "volcanoes and earthquakes reshape the surface of the earth"));

    private static final Corpus CORPUS = new Corpus("1.0", DOCS.size(), "corpus-sha", DOCS);

    private final CorpusRepository corpusRepository = new FakeCorpusRepository(CORPUS);
    private final EmbeddingRepository localEmbeddingRepository = new FakeEmbeddingRepository();
    private final EmbeddingRepository apiEmbeddingRepository = new FakeEmbeddingRepository();

    private final FakeRequestCache<ClusteringCacheKey, LinkageRunResult> cache = new FakeRequestCache<>();

    private final ClusteringService service =
            new ClusteringService(corpusRepository, localEmbeddingRepository, apiEmbeddingRepository, cache);

    @Test
    void runDefaultsToTfIdfCosineAndAllFourLinkagesEachWithNMinusOneRows() {
        List<LinkageRunResult> results = service.run(null, List.of());

        assertThat(results).extracting(LinkageRunResult::linkageId)
                .containsExactly("single", "complete", "average", "ward");
        assertThat(results).allSatisfy(r -> assertThat(r.rows()).hasSize(DOCS.size() - 1));
        assertThat(results).allSatisfy(r -> assertThat(r.leafOrder()).hasSize(DOCS.size()));
    }

    @Test
    void evaluationIsComputedOnlyAtTheFixedCutsIntersectedWithTwoToNMinusOne() {
        List<LinkageRunResult> results = service.run(Representation.TFIDF_COSINE, List.of("single"));

        ClusteringEvaluationBlock evaluation = results.get(0).evaluation();
        // n=5 -> n-1=4, so {2,3,4,5} ∩ [2,4] = {2,3,4}; k=5 must never appear.
        assertThat(evaluation.meanSilhouetteByK().keySet()).containsExactlyInAnyOrder(2, 3, 4);
        assertThat(evaluation.daviesBouldinByK().keySet()).containsExactlyInAnyOrder(2, 3, 4);
        assertThat(evaluation.copheneticCorrelation()).isBetween(-1.0, 1.0);
    }

    /**
     * Asserting only {@code copheneticCorrelation} would leave {@code meanSilhouetteByK}/
     * {@code daviesBouldinByK} unchecked. Comparing against {@code service} (this class's shared
     * cache) would make the two evaluation blocks trivially identical — {@code evaluateOnly}
     * delegates to {@code run}, so a second call for the same key is a cache hit returning
     * the very same {@link LinkageRunResult} instance, proving nothing about the k-maps'
     * actual content. {@code independentService} below has its own, separate cache, so
     * {@code full} and {@code evaluationOnly} are two genuinely independent computations of
     * the same input — their equality is a real assertion.
     */
    @Test
    void evaluateOnlyReturnsTheSameEvaluationBlockAsRunWithoutRowsOrLeafOrder() {
        ClusteringService independentService = new ClusteringService(
                corpusRepository, localEmbeddingRepository, apiEmbeddingRepository, new FakeRequestCache<>());

        List<LinkageRunResult> full = service.run(Representation.TFIDF_COSINE, List.of("ward"));
        List<LinkageEvaluationOnly> evaluationOnly =
                independentService.evaluateOnly(Representation.TFIDF_COSINE, List.of("ward"));

        assertThat(evaluationOnly).hasSize(1);
        assertThat(evaluationOnly.get(0).linkageId()).isEqualTo("ward");
        ClusteringEvaluationBlock evaluation = evaluationOnly.get(0).evaluation();
        ClusteringEvaluationBlock fullEvaluation = full.get(0).evaluation();
        assertThat(evaluation.copheneticCorrelation()).isEqualTo(fullEvaluation.copheneticCorrelation());
        assertThat(evaluation.meanSilhouetteByK()).isNotEmpty().isEqualTo(fullEvaluation.meanSilhouetteByK());
        assertThat(evaluation.daviesBouldinByK()).isNotEmpty().isEqualTo(fullEvaluation.daviesBouldinByK());
    }

    /**
     * Each {@code LinkageRunResult} carries {@code documentIds}, the
     * document behind each observation index, in {@code corpus.json} order. The fixture
     * corpus here is deliberately *not* alphabetical by id (d03, d01, d05, d02, d04), so a
     * bug that sorted ids before returning them (instead of reusing the exact order the
     * distance matrix was built from) would still fail this assertion.
     */
    @Test
    void runsDocumentIdsMatchCorpusOrderNotAlphabeticalOrder() {
        List<CorpusDocument> unsortedDocs = List.of(
                doc("d03", "stock markets and interest rates move the global economy"),
                doc("d01", "cats and dogs are common household pets around the world"),
                doc("d05", "volcanoes and earthquakes reshape the surface of the earth"),
                doc("d02", "dogs are loyal pets that live with families at home"),
                doc("d04", "interest rates set by central banks affect stock markets"));
        Corpus unsortedCorpus = new Corpus("1.0", unsortedDocs.size(), "corpus-sha", unsortedDocs);
        ClusteringService unsortedService = new ClusteringService(new FakeCorpusRepository(unsortedCorpus),
                localEmbeddingRepository, apiEmbeddingRepository, new FakeRequestCache<>());

        List<LinkageRunResult> results = unsortedService.run(Representation.TFIDF_COSINE, List.of("single"));

        assertThat(results).allSatisfy(r -> assertThat(r.documentIds())
                .as("documentIds must mirror corpus.json order, not a sorted order")
                .containsExactly("d03", "d01", "d05", "d02", "d04"));
    }

    @Test
    void cutAtAFreeKReturnsLabelsForExactlyThatK() {
        ClusteringCutResult result = service.cut(Representation.TFIDF_COSINE, "average", 2);

        assertThat(result.assignment().k()).isEqualTo(2);
        assertThat(result.assignment().labels()).hasSize(DOCS.size());
    }

    /**
     * {@code /clustering/cut}'s result also carries {@code
     * documentIds}, aligned with {@code labels} position by position, in {@code corpus.json}
     * order. Same non-alphabetical fixture as {@code runsDocumentIdsMatchCorpusOrder...} so a
     * sorted-id bug would still fail here.
     */
    @Test
    void cutDocumentIdsMatchCorpusOrderAndAlignWithLabels() {
        List<CorpusDocument> unsortedDocs = List.of(
                doc("d03", "stock markets and interest rates move the global economy"),
                doc("d01", "cats and dogs are common household pets around the world"),
                doc("d05", "volcanoes and earthquakes reshape the surface of the earth"),
                doc("d02", "dogs are loyal pets that live with families at home"),
                doc("d04", "interest rates set by central banks affect stock markets"));
        Corpus unsortedCorpus = new Corpus("1.0", unsortedDocs.size(), "corpus-sha", unsortedDocs);
        ClusteringService unsortedService = new ClusteringService(new FakeCorpusRepository(unsortedCorpus),
                localEmbeddingRepository, apiEmbeddingRepository, new FakeRequestCache<>());

        ClusteringCutResult result = unsortedService.cut(Representation.TFIDF_COSINE, "average", 2);

        assertThat(result.documentIds())
                .as("documentIds must mirror corpus.json order, not a sorted order")
                .containsExactly("d03", "d01", "d05", "d02", "d04");
        assertThat(result.documentIds())
                .as("documentIds must be aligned position-by-position with labels")
                .hasSameSizeAs(result.assignment().labels());
    }

    @Test
    void cutRejectsKOutsideTwoToNMinusOne() {
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", DOCS.size()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Before this fix,
     * {@code cut()} passed a bad {@code k} straight to {@code LinkageCut.cut}, which throws
     * a <em>raw</em> {@link IllegalArgumentException} — a raw IAE is treated as a server fault
     * and answers 500. A client-supplied {@code k}
     * outside {@code [2, n-1]} is a request-validation failure, not a server bug, so this
     * boundary must throw the dedicated {@link InvalidRequestException} subtype instead,
     * *before* the domain ever sees the bad value.
     */
    @Test
    void cutRejectsAnOutOfRangeKWithInvalidRequestExceptionNotARawOne() {
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", DOCS.size()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.INVALID_CUT);
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", 1))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.INVALID_CUT);
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", 0))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.INVALID_CUT);
    }

    /**
     * Under the fixed status-code rule, {@code linkage}/{@code linkages} is never
     * a path segment on any clustering endpoint, so — unlike a similarity algorithm id — an
     * unknown one has no ambiguous caller to defer to and is 400, not 404. Before this fix
     * this was {@link ResourceNotFoundException} (404); 404 is fixed to path-identified
     * resources only.
     */
    @Test
    void runRejectsAnUnknownLinkageId() {
        assertThatThrownBy(() -> service.run(Representation.TFIDF_COSINE, List.of("does-not-exist")))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.UNKNOWN_LINKAGE);
    }

    /**
     * Under the statelessness rule, no endpoint may depend on a previous run —
     * request X's result must be identical whether or not another request ran before or
     * after it. Caching does not violate this: it changes how fast X is computed, never
     * what X computes. Running X, then a different request Y, then X again must yield a
     * result for X equal to the very first X (here: literally the same cached instance).
     */
    @Test
    void runningADifferentRequestBetweenTwoIdenticalRunsNeverChangesTheSecondResult() {
        List<LinkageRunResult> firstX = service.run(Representation.TFIDF_COSINE, List.of("single"));
        service.run(Representation.TFIDF_COSINE, List.of("ward"));
        List<LinkageRunResult> secondX = service.run(Representation.TFIDF_COSINE, List.of("single"));

        assertThat(secondX).isEqualTo(firstX);
        assertThat(secondX.get(0)).as("must reuse the cached computation, not a fresh equal one")
                .isSameAs(firstX.get(0));
    }

    /**
     * By design, {@code cut} reuses the same
     * per-linkage cache {@code run} populates, so cutting a tree {@code run} already computed
     * must not recompute it, yet {@code cut}'s own {@code k} validation must still fire for
     * an out-of-range {@code k} on that cached tree.
     */
    @Test
    void cutAfterRunReusesTheCachedTreeAndStillValidatesK() {
        service.run(Representation.TFIDF_COSINE, List.of("average"));
        int cacheSizeAfterRun = cache.size();

        ClusteringCutResult result = service.cut(Representation.TFIDF_COSINE, "average", 3);

        assertThat(cacheSizeAfterRun).isEqualTo(1);
        assertThat(cache.size()).as("cut must not add a second entry for the same key").isEqualTo(1);
        assertThat(result.assignment().k()).isEqualTo(3);

        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", DOCS.size()))
                .isInstanceOf(InvalidRequestException.class);
    }

    /**
     * The cache holds immutable results only, so a
     * caller can never mutate a cached value. {@link LinkageRunResult}'s compact
     * constructor already defensively copies its lists, so the cached value is immutable by
     * construction with no extra wrapper needed — this proves that guarantee actually holds
     * for what {@code run} hands back.
     */
    @Test
    void theCachedLinkageResultRowsAndLeafOrderAreImmutable() {
        List<LinkageRunResult> results = service.run(Representation.TFIDF_COSINE, List.of("single"));
        LinkageRunResult cached = results.get(0);

        assertThatThrownBy(() -> cached.rows().add(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cached.leafOrder().add(99)).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A bare {@code cut} with no prior
     * {@code run} for that key must still populate the shared {@link ClusteringCacheKey}
     * cache (the {@code orElseGet} miss branch in {@code cut}), not just compute and discard.
     */
    @Test
    void aBareCutWithNoPriorRunPopulatesTheSharedCache() {
        assertThat(cache.size()).isZero();

        service.cut(Representation.TFIDF_COSINE, "average", 2);

        assertThat(cache.size()).isEqualTo(1);
    }

    /**
     * The embedding caches are bound to {@code corpusSha256} and verified at startup, so a
     * corpus document with no cached vector cannot happen on a correctly started server. If
     * it does, the server's own data is inconsistent: the client asked for a valid
     * representation and supplied nothing wrong, so answering "not found" (404) would blame
     * it for the server's state. It must surface as a server fault instead.
     */
    @Test
    void aCorpusDocumentMissingFromTheEmbeddingCacheIsAServerFaultNotANotFound() {
        // The fake cache holds no vectors, so every corpus document is a cache miss.
        assertThatThrownBy(() -> service.run(Representation.EMBEDDING_LOCAL, List.of("single")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ResourceNotFoundException.class);
    }

    private static CorpusDocument doc(String id, String abstractText) {
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText, "pdf", "grobid", true,
                "sha-" + id);
    }

    private static final class FakeCorpusRepository implements CorpusRepository {
        private final Corpus corpus;

        private FakeCorpusRepository(Corpus corpus) {
            this.corpus = corpus;
        }

        @Override
        public Corpus load() {
            return corpus;
        }

        @Override
        public void save(Corpus corpus) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }

    /** Not exercised by the tfidf-cosine-only cases above; present so the service compiles
     * against both required EmbeddingRepository dependencies. */
    private static final class FakeEmbeddingRepository implements EmbeddingRepository {
        @Override
        public EmbeddingCache load() {
            return new EmbeddingCache("1.0", "1.0", "corpus-sha", "m", 2, List.<EmbeddingVector>of());
        }

        @Override
        public void save(EmbeddingCache cache) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
