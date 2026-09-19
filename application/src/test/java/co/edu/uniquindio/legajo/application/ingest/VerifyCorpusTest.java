package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.corpus.CorpusRule;
import co.edu.uniquindio.legajo.corpus.CorpusVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VerifyCorpus} is a thin wrapper: load through the port, delegate to {@code
 * CorpusVerifier}. These tests confirm the wiring, not the rules themselves (which
 * {@code CorpusVerifierTest} already covers exhaustively).
 */
class VerifyCorpusTest {

    @Test
    void reportsNoViolationsForAFullyValidCorpus() {
        Corpus corpus = validCorpus();
        VerifyCorpus verifyCorpus = new VerifyCorpus(new InMemoryCorpusRepository(corpus));

        CorpusVerificationResult result = verifyCorpus.verify();

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void surfacesViolationsFromTheLoadedCorpus() {
        CorpusDocument notValidated = document("d01", "an abstract with enough real words", false);
        Corpus corpus = corpusOf(notValidated, document("d02", "another abstract with real words", true),
                document("d03", "a third abstract with real words", true));
        VerifyCorpus verifyCorpus = new VerifyCorpus(new InMemoryCorpusRepository(corpus));

        CorpusVerificationResult result = verifyCorpus.verify();

        assertThat(result.isValid()).isFalse();
        assertThat(result.violations()).anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.MANUALLY_VALIDATED));
    }

    private static Corpus validCorpus() {
        return corpusOf(
                document("d01", "the first abstract has enough real words", true),
                document("d02", "the second abstract has enough real words", true),
                document("d03", "the third abstract has enough real words", true));
    }

    private static CorpusDocument document(String id, String abstractText, boolean validated) {
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText, "data/pdfs/" + id + ".pdf",
                "GROBID", validated, CorpusHasher.abstractSha256(abstractText));
    }

    private static Corpus corpusOf(CorpusDocument... documents) {
        List<CorpusDocument> docs = List.of(documents);
        return new Corpus("1.0", docs.size(), CorpusHasher.corpusSha256(docs), docs);
    }
}
