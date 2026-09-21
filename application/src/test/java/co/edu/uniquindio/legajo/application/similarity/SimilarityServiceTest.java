package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.application.cache.FakeRequestCache;
import co.edu.uniquindio.legajo.application.cache.NoOpRequestCache;
import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ProblemType;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.application.error.UnknownIdentifierException;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.AlgorithmKind;
import co.edu.uniquindio.legajo.similarity.AlgorithmTrace;
import co.edu.uniquindio.legajo.similarity.DpMatrixTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingApi;
import co.edu.uniquindio.legajo.similarity.EmbeddingApiTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocal;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocalTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import co.edu.uniquindio.legajo.similarity.Jaccard;
import co.edu.uniquindio.legajo.similarity.JaccardTrace;
import co.edu.uniquindio.legajo.similarity.Levenshtein;
import co.edu.uniquindio.legajo.similarity.NeedlemanWunsch;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;
import co.edu.uniquindio.legajo.similarity.TfIdfCosineTrace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * TRD §6.6 (similarity endpoints) / TAC-01, TAC-05, TAC-06: pure orchestration over the
 * domain's six {@code SimilarityAlgorithm} capabilities, the corpus, and the two embedding
 * caches. No Spring, no HTTP, no DTOs.
 */
class SimilarityServiceTest {

    private static final CorpusDocument DOC_A = new CorpusDocument(
            "d01", "Neural machine translation", List.of("A"), "the cat sat on the mat", "pdf", "grobid", true, "s1");
    private static final CorpusDocument DOC_B = new CorpusDocument(
            "d02", "Statistical parsing", List.of("B"), "the dog sat on the log", "pdf", "grobid", true, "s2");
    private static final CorpusDocument DOC_C = new CorpusDocument(
            "d03", "Graph clustering", List.of("C"), "completely unrelated abstract text here", "pdf", "grobid",
            true, "s3");

    private static final Corpus CORPUS = new Corpus("1.0", 3, "corpus-sha", List.of(DOC_A, DOC_B, DOC_C));

    private static final EmbeddingVector VECTOR_A = new EmbeddingVector("d01", "local", "m", 1.0, List.of(1.0, 0.0));
    private static final EmbeddingVector VECTOR_B = new EmbeddingVector("d02", "local", "m", 1.0, List.of(0.6, 0.8));
    private static final EmbeddingVector VECTOR_C = new EmbeddingVector("d03", "local", "m", 1.0, List.of(0.0, 1.0));

    private static final EmbeddingVector API_VECTOR_A = new EmbeddingVector("d01", "api", "m", 1.0, List.of(1.0, 0.0));
    private static final EmbeddingVector API_VECTOR_B = new EmbeddingVector("d02", "api", "m", 1.0, List.of(0.8, 0.6));
    private static final EmbeddingVector API_VECTOR_C = new EmbeddingVector("d03", "api", "m", 1.0, List.of(0.0, 1.0));

    private final CorpusRepository corpusRepository = new FakeCorpusRepository(CORPUS);
    private final EmbeddingRepository localEmbeddingRepository =
            new FakeEmbeddingRepository(List.of(VECTOR_A, VECTOR_B, VECTOR_C));
    private final EmbeddingRepository apiEmbeddingRepository =
            new FakeEmbeddingRepository(List.of(API_VECTOR_A, API_VECTOR_B, API_VECTOR_C));

    private final SimilarityAlgorithmRegistry registry = new SimilarityAlgorithmRegistry(List.of(
            new Levenshtein(), new NeedlemanWunsch(), new Jaccard(), new TfIdfCosine(),
            new EmbeddingLocal(), new EmbeddingApi()));

    private final FakeRequestCache<SimilarityCacheKey, SimilarityResult> cache = new FakeRequestCache<>();

    private final SimilarityService service = new SimilarityService(
            corpusRepository, registry, localEmbeddingRepository, apiEmbeddingRepository, cache);

    @Test
    void catalogueReturnsExactlyTheSixFixedAlgorithmsInRegistryOrder() {
        List<AlgorithmSummary> catalogue = service.catalogue();

        assertThat(catalogue).extracting(AlgorithmSummary::id).containsExactly(
                "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");
        assertThat(catalogue).extracting(AlgorithmSummary::kind).containsExactly(
                AlgorithmKind.CLASSIC, AlgorithmKind.CLASSIC, AlgorithmKind.CLASSIC, AlgorithmKind.CLASSIC,
                AlgorithmKind.AI, AlgorithmKind.AI);
    }

    @Test
    void compareDefaultsToAllSixAlgorithmsWhenNoAlgorithmIdsAreGiven() {
        List<AlgorithmSimilarity> results = service.compare("d01", "d02", List.of());

        assertThat(results).hasSize(6);
        assertThat(results).extracting(AlgorithmSimilarity::algorithmId).containsExactly(
                "levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine", "embedding-local", "embedding-api");
        assertThat(results).allSatisfy(r -> assertThat(r.result().normalizedScore()).isBetween(0.0, 1.0));
    }

    @Test
    void compareWithExplicitAlgorithmIdsOnlyRunsThose() {
        List<AlgorithmSimilarity> results = service.compare("d01", "d02", List.of("jaccard"));

        assertThat(results).extracting(AlgorithmSimilarity::algorithmId).containsExactly("jaccard");
    }

    /**
     * Task A7 (TRD §6.6's fixed status-code rule): {@code algorithmIds} is a request-body
     * field on {@code compare}, and {@link SimilarityService} cannot know that from inside
     * {@code requireAlgorithm} (the same lookup is reused by {@code trace}'s path segment),
     * so the service raises the location-agnostic {@link UnknownIdentifierException} and lets
     * {@code SimilarityController} — which does know its own request shape — reclassify it.
     * See {@code SimilarityControllerTest} for the REST-level 400 this becomes.
     */
    @Test
    void compareRejectsAnUnknownAlgorithmId() {
        assertThatThrownBy(() -> service.compare("d01", "d02", List.of("does-not-exist")))
                .isInstanceOf(UnknownIdentifierException.class)
                .extracting(t -> ((UnknownIdentifierException) t).type())
                .isEqualTo(ProblemType.UNKNOWN_ALGORITHM);
    }

    /**
     * The document id has already passed the corpus lookup, so a missing cached vector is an
     * inconsistency in the server's own caches, not something the client asked for wrongly.
     * See {@code ClusteringServiceTest} for the same rule on the clustering path.
     */
    @Test
    void aKnownDocumentMissingFromTheEmbeddingCacheIsAServerFaultNotANotFound() {
        SimilarityService withIncompleteCache = new SimilarityService(
                corpusRepository, registry, new FakeEmbeddingRepository(List.of()), apiEmbeddingRepository,
                new FakeRequestCache<>());

        assertThatThrownBy(() -> withIncompleteCache.compare("d01", "d02", List.of("embedding-local")))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Task A7: a document id is never a path segment anywhere in this service, so, unlike an
     * algorithm id, {@code requireDocument} can throw the final {@link InvalidRequestException}
     * (400, {@link ProblemType#UNKNOWN_DOCUMENT}) directly.
     */
    @Test
    void compareRejectsAnUnknownDocumentId() {
        assertThatThrownBy(() -> service.compare("does-not-exist", "d02", List.of("jaccard")))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.UNKNOWN_DOCUMENT);
    }

    @Test
    void matrixIsSquareOverTheSelectionWithADiagonalOfOne() {
        List<List<CachedSimilarityResult>> matrix = service.matrix(List.of("d01", "d02", "d03"), "jaccard");

        assertThat(matrix).hasSize(3);
        for (int i = 0; i < 3; i++) {
            assertThat(matrix.get(i)).hasSize(3);
            assertThat(matrix.get(i).get(i).result().normalizedScore()).isCloseTo(1.0, within(1e-9));
        }
    }

    @Test
    void matrixRejectsASelectionSmallerThanThree() {
        assertThatThrownBy(() -> service.matrix(List.of("d01", "d02"), "jaccard"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.INVALID_SELECTION);
    }

    /** TRD §6.6 (task A7): a matrix selection with a duplicate id is the same {@code
     * invalid-selection} URN as an undersized one, just a different concrete reason. */
    @Test
    void matrixRejectsASelectionWithADuplicateDocumentId() {
        assertThatThrownBy(() -> service.matrix(List.of("d01", "d01", "d02"), "jaccard"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.INVALID_SELECTION);
    }

    /** Task A7: {@code matrix}'s {@code algorithmId} is a request-body field, so an unknown
     * one is the same location-agnostic {@link UnknownIdentifierException} {@code compare}
     * raises, reclassified to 400 by {@code SimilarityController} (never 404 here). */
    @Test
    void matrixRejectsAnUnknownAlgorithmId() {
        assertThatThrownBy(() -> service.matrix(List.of("d01", "d02", "d03"), "does-not-exist"))
                .isInstanceOf(UnknownIdentifierException.class)
                .extracting(t -> ((UnknownIdentifierException) t).type())
                .isEqualTo(ProblemType.UNKNOWN_ALGORITHM);
    }

    @Test
    void matrixWorksForBothEmbeddingCapabilitiesUsingTheirOwnCache() {
        List<List<CachedSimilarityResult>> local = service.matrix(List.of("d01", "d02", "d03"), "embedding-local");
        List<List<CachedSimilarityResult>> api = service.matrix(List.of("d01", "d02", "d03"), "embedding-api");

        assertThat(local.get(0).get(0).result().normalizedScore()).isCloseTo(1.0, within(1e-9));
        assertThat(api.get(0).get(0).result().normalizedScore()).isCloseTo(1.0, within(1e-9));
    }

    /**
     * TRD §9 / task A5: a second identical compare must be a cache hit reporting the exact
     * same result the first (fresh) call computed — including {@code computedNanos}, which
     * is measured inside {@code compute()} and stored with the cached entry, never
     * re-measured on a hit (a hit's lookup itself takes far less time than a real
     * computation, so re-measuring would silently prove nothing).
     */
    @Test
    void anIdenticalSecondCompareIsACacheHitWithTheExactSameResultAsTheFirst() {
        List<AlgorithmSimilarity> first = service.compare("d01", "d02", List.of("levenshtein"));
        List<AlgorithmSimilarity> second = service.compare("d01", "d02", List.of("levenshtein"));

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.get(0).cached()).as("first call must be a fresh computation").isFalse();
        assertThat(second.get(0).cached()).as("second identical call must be a cache hit").isTrue();
        assertThat(second.get(0).result()).isEqualTo(first.get(0).result());
    }

    @Test
    void aDifferentPairIsNeverACacheHitForAnAlreadyCachedPair() {
        service.compare("d01", "d02", List.of("levenshtein"));

        List<AlgorithmSimilarity> differentPair = service.compare("d01", "d03", List.of("levenshtein"));

        assertThat(differentPair.get(0).cached()).isFalse();
    }

    @Test
    void aDifferentAlgorithmOnTheSamePairIsNeverACacheHitForAnAlreadyCachedAlgorithm() {
        service.compare("d01", "d02", List.of("levenshtein"));

        List<AlgorithmSimilarity> differentAlgorithm = service.compare("d01", "d02", List.of("jaccard"));

        assertThat(differentAlgorithm.get(0).cached()).isFalse();
    }

    /** NFR-QA-01: the benchmark harness disables caching by wiring {@code NoOpRequestCache}. */
    @Test
    void withTheNoOpCacheEveryCallIsAFreshComputationNeverCached() {
        SimilarityService withNoCache = new SimilarityService(
                corpusRepository, registry, localEmbeddingRepository, apiEmbeddingRepository,
                new NoOpRequestCache<>());

        withNoCache.compare("d01", "d02", List.of("levenshtein"));
        List<AlgorithmSimilarity> second = withNoCache.compare("d01", "d02", List.of("levenshtein"));

        assertThat(second.get(0).cached()).isFalse();
    }

    @Test
    void matrixCellsShareTheSameCacheAsCompareByAlgorithmAndDirectionalPair() {
        // Pre-populate the cache exactly as compare() would for the (jaccard, d01, d02) key.
        service.compare("d01", "d02", List.of("jaccard"));

        List<List<CachedSimilarityResult>> matrix = service.matrix(List.of("d01", "d02", "d03"), "jaccard");

        assertThat(matrix.get(0).get(1).cached())
                .as("matrix cell (d01,d02) must reuse the entry compare() just populated")
                .isTrue();
        assertThat(matrix.get(0).get(0).cached())
                .as("the self-pair (d01,d01) was never computed before, so it must still be a miss")
                .isFalse();
    }

    /**
     * {@code R3-trace-test-overclaims}: this test's name promised the trace's value "matches
     * the computed score", but the original body only asserted presence. Mirrors {@code
     * SimilarityEndToEndTest.tac06TheTraceValueAlwaysEqualsThePublishedScore}'s per-capability
     * extraction: the DP matrix's bottom-right cell for the two DP algorithms (compared
     * against {@code rawValue}, never {@code normalizedScore}, since the DP algorithms report
     * a possibly-unnormalized raw distance/score) and the coefficient/cosine/normalizedScore
     * field for the other four (compared against {@code normalizedScore}).
     */
    @Test
    void traceIsAvailableForEveryOneOfTheSixCapabilitiesAndMatchesTheComputedScore() {
        List<AlgorithmSimilarity> results = service.compare("d01", "d02", List.of());
        assertThat(results).as("compare must return all six capabilities").hasSize(6);

        for (AlgorithmSimilarity row : results) {
            String algorithmId = row.algorithmId();
            Optional<AlgorithmTrace> trace = service.trace(algorithmId, "d01", "d02");
            assertThat(trace).as("trace for %s", algorithmId).isPresent();

            double traceValue = switch (trace.get()) {
                case DpMatrixTrace dp -> {
                    double[][] matrix = dp.matrix();
                    yield matrix[matrix.length - 1][matrix[matrix.length - 1].length - 1];
                }
                case JaccardTrace jaccard -> jaccard.coefficient();
                case TfIdfCosineTrace tfIdf -> tfIdf.cosine();
                case EmbeddingLocalTrace embeddingLocal -> embeddingLocal.normalizedScore();
                case EmbeddingApiTrace embeddingApi -> embeddingApi.normalizedScore();
            };
            double publishedValue = switch (algorithmId) {
                case "levenshtein", "needleman-wunsch" -> row.result().rawValue();
                default -> row.result().normalizedScore();
            };

            assertThat(traceValue).as("trace value for %s must equal the published score", algorithmId)
                    .isCloseTo(publishedValue, within(1e-9));
        }
    }

    /**
     * Task A7: {@code trace}'s {@code algorithmId} is a path segment on {@code GET
     * /similarity/{algorithmId}/trace}, but this service method has no way to know that
     * itself — {@code requireAlgorithm} is the exact same lookup {@code compare}/{@code
     * matrix} use for a body id — so it raises the same location-agnostic {@link
     * UnknownIdentifierException} either way; only {@code SimilarityController.trace}
     * reclassifies it to 404, since only it knows the request shape.
     */
    @Test
    void traceRejectsAnUnknownAlgorithmIdWithTheLocationAgnosticException() {
        assertThatThrownBy(() -> service.trace("does-not-exist", "d01", "d02"))
                .isInstanceOf(UnknownIdentifierException.class)
                .extracting(t -> ((UnknownIdentifierException) t).type())
                .isEqualTo(ProblemType.UNKNOWN_ALGORITHM);
    }

    /** Task A7: {@code documentIdA}/{@code documentIdB} are query parameters on {@code trace},
     * never a path segment, so {@code requireDocument} throws the final 400 directly. */
    @Test
    void traceRejectsAnUnknownDocumentIdDirectlyAsInvalidRequest() {
        assertThatThrownBy(() -> service.trace("levenshtein", "does-not-exist", "d02"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(t -> ((InvalidRequestException) t).type().orElseThrow())
                .isEqualTo(ProblemType.UNKNOWN_DOCUMENT);
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

    private static final class FakeEmbeddingRepository implements EmbeddingRepository {
        private final List<EmbeddingVector> vectors;

        private FakeEmbeddingRepository(List<EmbeddingVector> vectors) {
            this.vectors = vectors;
        }

        @Override
        public EmbeddingCache load() {
            return new EmbeddingCache("1.0", "1.0", "corpus-sha", "m", 2, vectors);
        }

        @Override
        public void save(EmbeddingCache cache) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
