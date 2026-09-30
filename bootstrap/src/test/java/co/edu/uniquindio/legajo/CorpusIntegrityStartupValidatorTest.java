package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CorpusIntegrityStartupValidatorTest {

    private static CorpusDocument document(String id, String abstractText) {
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText, "pdf", "grobid", true,
                CorpusHasher.abstractSha256(abstractText));
    }

    private static CorpusRepository repositoryOf(Corpus corpus) {
        return new CorpusRepository() {
            @Override
            public Corpus load() {
                return corpus;
            }

            @Override
            public void save(Corpus toSave) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void aCorpusWhoseStoredHashMatchesItsDocumentsStartsCleanly() {
        List<CorpusDocument> documents = List.of(document("d01", "alpha"), document("d02", "beta"));
        Corpus corpus = new Corpus("1.0", 2, CorpusHasher.corpusSha256(documents), documents);

        assertThatCode(() -> new CorpusIntegrityStartupValidator(repositoryOf(corpus)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void aStoredHashThatDiffersFromTheRecomputedOneStopsTheBootAndNamesTheVerifyCommand() {
        List<CorpusDocument> documents = List.of(document("d01", "alpha"), document("d02", "beta"));
        Corpus corpus = new Corpus("1.0", 2, "0".repeat(64), documents);

        assertThatIllegalStateException()
                .isThrownBy(() -> new CorpusIntegrityStartupValidator(repositoryOf(corpus)).validate())
                .withMessageContaining("verifyCorpus")
                .withMessageContaining("0".repeat(64))
                .withMessageContaining(CorpusHasher.corpusSha256(documents));
    }
}
