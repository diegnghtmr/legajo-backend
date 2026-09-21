package co.edu.uniquindio.legajo.application.embedding;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §6.6: {@code GET /embeddings/status} reports provider, model, dimension, device,
 * cached/live mode, the cache's {@code corpusSha256}, and {@code matchesCorpus}.
 *
 * <p>The TRD names this endpoint in the singular but never says whether it reports one
 * embedding family or both (local and API caches independently satisfy the two embedding
 * capabilities) — this service computes the status of one {@link EmbeddingRepository} at a
 * time so either shape is possible at the REST boundary (A3/A4) without redoing this logic;
 * see this class's Javadoc for the flagged ambiguity.
 */
class EmbeddingsServiceTest {

    private static final Corpus MATCHING_CORPUS = new Corpus("1.0", 1,
            "corpus-sha", List.of(new CorpusDocument("d01", "T", List.of("A"), "abstract", "pdf", "grobid", true, "s")));

    private static final EmbeddingVector VECTOR =
            new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 1.0, List.of(1.0, 0.0));

    @Test
    void statusReportsCacheMetadataAndDeviceAndMode() {
        EmbeddingsService service = new EmbeddingsService(new FakeCorpusRepository(MATCHING_CORPUS));
        EmbeddingRepository repository = new FakeEmbeddingRepository(
                new EmbeddingCache("1.0", "1.0", "corpus-sha", "all-MiniLM-L6-v2", 2, List.of(VECTOR)));

        EmbeddingStatus status = service.status(repository, "cpu", EmbeddingProviderMode.CACHED);

        assertThat(status.provider()).isEqualTo("local");
        assertThat(status.model()).isEqualTo("all-MiniLM-L6-v2");
        assertThat(status.dimension()).isEqualTo(2);
        assertThat(status.device()).isEqualTo("cpu");
        assertThat(status.mode()).isEqualTo(EmbeddingProviderMode.CACHED);
        assertThat(status.corpusSha256()).isEqualTo("corpus-sha");
        assertThat(status.matchesCorpus()).isTrue();
    }

    @Test
    void statusReportsMatchesCorpusFalseWhenTheCacheWasBuiltForADifferentCorpus() {
        EmbeddingsService service = new EmbeddingsService(new FakeCorpusRepository(MATCHING_CORPUS));
        EmbeddingRepository repository = new FakeEmbeddingRepository(
                new EmbeddingCache("1.0", "1.0", "a-different-sha", "all-MiniLM-L6-v2", 2, List.of(VECTOR)));

        EmbeddingStatus status = service.status(repository, "cpu", EmbeddingProviderMode.CACHED);

        assertThat(status.matchesCorpus()).isFalse();
        assertThat(status.corpusSha256()).isEqualTo("a-different-sha");
    }

    /**
     * {@code R3-empty-cache-provider-untested}: {@link EmbeddingsService#status} has no
     * vector to read a {@code provider} id from when the cache is empty, so it reports the
     * documented {@code "unknown"} fail-soft placeholder (see this class's Javadoc for why)
     * instead of throwing or guessing a real provider id.
     */
    @Test
    void statusReportsProviderUnknownWhenTheCacheHasNoVectors() {
        EmbeddingsService service = new EmbeddingsService(new FakeCorpusRepository(MATCHING_CORPUS));
        EmbeddingRepository repository = new FakeEmbeddingRepository(
                new EmbeddingCache("1.0", "1.0", "corpus-sha", "all-MiniLM-L6-v2", 2, List.of()));

        EmbeddingStatus status = service.status(repository, "cpu", EmbeddingProviderMode.CACHED);

        assertThat(status.provider()).isEqualTo("unknown");
        assertThat(status.model()).isEqualTo("all-MiniLM-L6-v2");
        assertThat(status.dimension()).isEqualTo(2);
        assertThat(status.matchesCorpus()).isTrue();
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
        private final EmbeddingCache cache;

        private FakeEmbeddingRepository(EmbeddingCache cache) {
            this.cache = cache;
        }

        @Override
        public EmbeddingCache load() {
            return cache;
        }

        @Override
        public void save(EmbeddingCache cache) {
            throw new UnsupportedOperationException("not needed by this test");
        }
    }
}
