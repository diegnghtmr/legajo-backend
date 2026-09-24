package co.edu.uniquindio.legajo.application.corpus;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /corpus} lists {@code id}/{@code title}/{@code authors} only;
 * {@code GET /corpus/{id}} returns the article plus the full abstract. This service is pure
 * orchestration over {@link CorpusRepository} — no Spring, no HTTP, no DTOs.
 */
class CorpusServiceTest {

    private static final CorpusDocument DOC_1 = new CorpusDocument(
            "d01", "Title One", List.of("Author A", "Author B"), "Abstract one.", "pdf", "grobid", true, "sha1");
    private static final CorpusDocument DOC_2 = new CorpusDocument(
            "d02", "Title Two", List.of("Author C"), "Abstract two.", "pdf", "grobid", true, "sha2");

    private static final Corpus CORPUS = new Corpus("1.0", 2, "corpus-sha", List.of(DOC_1, DOC_2));

    private final CorpusRepository repository = new FakeCorpusRepository(CORPUS);
    private final CorpusService service = new CorpusService(repository);

    @Test
    void listDocumentsReturnsIdTitleAndAuthorsOnlyInCorpusOrder() {
        List<CorpusSummary> summaries = service.listDocuments();

        assertThat(summaries).containsExactly(
                new CorpusSummary("d01", "Title One", List.of("Author A", "Author B")),
                new CorpusSummary("d02", "Title Two", List.of("Author C")));
    }

    @Test
    void findDocumentReturnsTheFullDocumentIncludingItsAbstract() {
        Optional<CorpusDocument> found = service.findDocument("d02");

        assertThat(found).contains(DOC_2);
    }

    @Test
    void findDocumentReturnsEmptyForAnUnknownId() {
        assertThat(service.findDocument("does-not-exist")).isEmpty();
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
}
