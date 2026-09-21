package co.edu.uniquindio.legajo.application.clustering;

import co.edu.uniquindio.legajo.application.error.InvalidRequestException;
import co.edu.uniquindio.legajo.application.error.ResourceNotFoundException;
import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
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
 * TRD §6.6 (clustering endpoints) / §6.4-§6.5 / TAC-03, TAC-04: pure orchestration wiring
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

    private final ClusteringService service =
            new ClusteringService(corpusRepository, localEmbeddingRepository, apiEmbeddingRepository);

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

    @Test
    void evaluateOnlyReturnsTheSameEvaluationBlockAsRunWithoutRowsOrLeafOrder() {
        List<LinkageRunResult> full = service.run(Representation.TFIDF_COSINE, List.of("ward"));
        List<LinkageEvaluationOnly> evaluationOnly = service.evaluateOnly(Representation.TFIDF_COSINE, List.of("ward"));

        assertThat(evaluationOnly).hasSize(1);
        assertThat(evaluationOnly.get(0).linkageId()).isEqualTo("ward");
        assertThat(evaluationOnly.get(0).evaluation().copheneticCorrelation())
                .isEqualTo(full.get(0).evaluation().copheneticCorrelation());
    }

    @Test
    void cutAtAFreeKReturnsLabelsForExactlyThatK() {
        ClusterAssignment assignment = service.cut(Representation.TFIDF_COSINE, "average", 2);

        assertThat(assignment.k()).isEqualTo(2);
        assertThat(assignment.labels()).hasSize(DOCS.size());
    }

    @Test
    void cutRejectsKOutsideTwoToNMinusOne() {
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", DOCS.size()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A4 requirement (feature doc {@code rest-api.md}, task A4): before this fix,
     * {@code cut()} passed a bad {@code k} straight to {@code LinkageCut.cut}, which throws
     * a <em>raw</em> {@link IllegalArgumentException} — since A3b's error-classification fix,
     * a raw IAE is treated as a server fault and answers 500. A client-supplied {@code k}
     * outside {@code [2, n-1]} is a request-validation failure, not a server bug, so this
     * boundary must throw the dedicated {@link InvalidRequestException} subtype instead,
     * *before* the domain ever sees the bad value.
     */
    @Test
    void cutRejectsAnOutOfRangeKWithInvalidRequestExceptionNotARawOne() {
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", DOCS.size()))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", 1))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.cut(Representation.TFIDF_COSINE, "average", 0))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void runRejectsAnUnknownLinkageId() {
        assertThatThrownBy(() -> service.run(Representation.TFIDF_COSINE, List.of("does-not-exist")))
                .isInstanceOf(ResourceNotFoundException.class);
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
