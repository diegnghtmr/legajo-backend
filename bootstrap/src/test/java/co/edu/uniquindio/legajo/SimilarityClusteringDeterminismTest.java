package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.cache.NoOpRequestCache;
import co.edu.uniquindio.legajo.application.clustering.ClusteringService;
import co.edu.uniquindio.legajo.application.clustering.LinkageRunResult;
import co.edu.uniquindio.legajo.application.similarity.AlgorithmSimilarity;
import co.edu.uniquindio.legajo.application.similarity.CachedSimilarityResult;
import co.edu.uniquindio.legajo.application.similarity.SimilarityService;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.MiniLmEmbedder;
import co.edu.uniquindio.legajo.infrastructure.embedding.OpenAiCompatibleEmbedder;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingApi;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocal;
import co.edu.uniquindio.legajo.similarity.Jaccard;
import co.edu.uniquindio.legajo.similarity.Levenshtein;
import co.edu.uniquindio.legajo.similarity.NeedlemanWunsch;
import co.edu.uniquindio.legajo.similarity.Representation;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithmRegistry;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two independent runs over cached embeddings must return equal results — proved over the real,
 * versioned {@code data/corpus.json}/{@code data/embeddings-*.json} — the same cached-mode
 * data every other trusting path in this codebase relies on.
 *
 * <p><b>Why this lives in {@code bootstrap}, not {@code application}.</b> {@code application}
 * depends only on {@code :domain}
 * (its {@code build.gradle.kts} declares no {@code infrastructure} dependency, by design —
 * the hexagonal boundary ArchUnit enforces), so it has no way to construct the real {@link
 * JsonCorpusRepository}/{@link JsonEmbeddingRepository} adapters this test needs to load the
 * real corpus/caches; every existing {@code application} test uses an in-memory fake instead
 * (the real, cached, no-network data belongs to {@code bootstrap}/{@code
 * infrastructure}). {@code bootstrap} already depends on {@code infrastructure}, exactly as
 * the offline CLI tests ({@code IngestCliTest}, {@code VerifyCorpusCliTest}) and {@code
 * DomainConfigurationTest} do, so this test lives here instead.
 *
 * <p><b>No Spring context, no cache reuse.</b> An earlier version of this test suite either
 * compared a cache hit to itself (equal by construction, since it is
 * literally the same stored instance) or ran a {@code NoOpRequestCache} once and only
 * asserted "never cached", never comparing two independently computed values — neither
 * proves that two independent runs actually agree. Each test method below builds two
 * <em>independent</em>
 * service instances, each wired directly (no Spring) over the same real repositories with
 * its own fresh {@link NoOpRequestCache}, so neither call can possibly reuse the other's
 * result, and then compares their outputs value-by-value ({@code isEqualTo} on records whose
 * {@code equals} covers every field with {@link Double#compare}-correct semantics for
 * {@code double}s, or an explicit {@link Double#compare} where a raw score is extracted).
 * {@link SimilarityResult#computedNanos()} is deliberately excluded from the similarity
 * comparisons: it is real wall-clock measurement, expected to differ between two independent
 * runs, and carries no similarity/clustering value of its own (this test is about the computed
 * result, not about the two runs racing to the same duration).
 */
class SimilarityClusteringDeterminismTest {

    private final CorpusRepository corpusRepository = new JsonCorpusRepository(Path.of("data/corpus.json"));
    private final Corpus corpus = corpusRepository.load();
    private final EmbeddingRepository localEmbeddingRepository = new JsonEmbeddingRepository(
            Path.of("data/embeddings-minilm.json"), MiniLmEmbedder.PROVIDER, corpus.corpusSha256());
    private final EmbeddingRepository apiEmbeddingRepository = new JsonEmbeddingRepository(
            Path.of("data/embeddings-openai.json"), OpenAiCompatibleEmbedder.PROVIDER, corpus.corpusSha256());

    private SimilarityService freshSimilarityService() {
        return new SimilarityService(corpusRepository, freshRegistry(), localEmbeddingRepository,
                apiEmbeddingRepository, new NoOpRequestCache<>());
    }

    private static SimilarityAlgorithmRegistry freshRegistry() {
        return new SimilarityAlgorithmRegistry(List.of(
                new Levenshtein(), new NeedlemanWunsch(), new Jaccard(), new TfIdfCosine(),
                new EmbeddingLocal(), new EmbeddingApi()));
    }

    private ClusteringService freshClusteringService() {
        return new ClusteringService(corpusRepository, localEmbeddingRepository, apiEmbeddingRepository,
                new NoOpRequestCache<>());
    }

    @Test
    void compareOverAllSixCapabilitiesIsEqualAcrossTwoIndependentFreshComputations() {
        String documentIdA = corpus.documents().get(0).id();
        String documentIdB = corpus.documents().get(1).id();

        List<AlgorithmSimilarity> first = freshSimilarityService().compare(documentIdA, documentIdB, List.of());
        List<AlgorithmSimilarity> second = freshSimilarityService().compare(documentIdA, documentIdB, List.of());

        assertThat(first).as("compare must return all six capabilities").hasSize(6);
        assertThat(second).hasSize(first.size());
        for (int i = 0; i < first.size(); i++) {
            AlgorithmSimilarity a = first.get(i);
            AlgorithmSimilarity b = second.get(i);
            assertThat(b.algorithmId()).as("algorithmId[%d]", i).isEqualTo(a.algorithmId());
            assertThat(a.cached()).as("first run must never be a cache hit").isFalse();
            assertThat(b.cached()).as("second, independent run must never be a cache hit either").isFalse();
            assertSameResult(a.algorithmId(), a.result(), b.result());
        }
    }

    @Test
    void matrixOverTfIdfCosineIsEqualAcrossTwoIndependentFreshComputations() {
        List<String> documentIds = corpus.documents().stream().limit(5).map(CorpusDocument::id).toList();

        List<List<CachedSimilarityResult>> first = freshSimilarityService().matrix(documentIds, "tfidf-cosine");
        List<List<CachedSimilarityResult>> second = freshSimilarityService().matrix(documentIds, "tfidf-cosine");

        assertThat(first).as("matrix must be m x m").hasSize(documentIds.size());
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i)).hasSize(first.get(i).size());
            for (int j = 0; j < first.get(i).size(); j++) {
                CachedSimilarityResult a = first.get(i).get(j);
                CachedSimilarityResult b = second.get(i).get(j);
                assertThat(a.cached()).as("first run, cell (%d,%d)", i, j).isFalse();
                assertThat(b.cached()).as("second, independent run, cell (%d,%d)", i, j).isFalse();
                assertSameResult("tfidf-cosine cell (%d,%d)".formatted(i, j), a.result(), b.result());
            }
        }
    }

    /**
     * Cached embeddings are explicitly in scope: one clustering run per representation,
     * including the two embedding-backed ones, over independent instances.
     */
    @ParameterizedTest
    @EnumSource(Representation.class)
    void clusteringRunIsEqualAcrossTwoIndependentFreshComputationsForEveryRepresentation(Representation representation) {
        List<LinkageRunResult> first = freshClusteringService().run(representation, List.of());
        List<LinkageRunResult> second = freshClusteringService().run(representation, List.of());

        assertThat(first).as("run must return all four linkages for %s", representation).hasSize(4);
        assertThat(second).as(representation.id()).isEqualTo(first);
    }

    private void assertSameResult(String label, SimilarityResult first, SimilarityResult second) {
        assertThat(Double.compare(second.normalizedScore(), first.normalizedScore()))
                .as("normalizedScore for %s", label).isZero();
        if (first.rawValue() == null) {
            assertThat(second.rawValue()).as("rawValue for %s", label).isNull();
        } else {
            assertThat(second.rawValue()).as("rawValue for %s", label).isNotNull();
            assertThat(Double.compare(second.rawValue(), first.rawValue())).as("rawValue for %s", label).isZero();
        }
        assertThat(second.degenerate()).as("degenerate for %s", label).isEqualTo(first.degenerate());
    }
}
