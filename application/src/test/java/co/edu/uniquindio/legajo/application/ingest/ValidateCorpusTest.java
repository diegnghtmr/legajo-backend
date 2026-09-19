package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link ValidateCorpus} is the only place {@code manuallyValidated} is ever set to
 * {@code true} (TRD §6.1, item 5) — always by an explicit call naming ids or "all",
 * never automatically. Validating also (re)freezes {@code abstractSha256} and
 * recomputes {@code corpusSha256} for the updated document list.
 */
class ValidateCorpusTest {

    @Test
    void validateIdsMarksOnlyTheNamedDocumentsAsManuallyValidated() {
        Corpus corpus = corpusOf(unvalidated("d01"), unvalidated("d02"), unvalidated("d03"));
        InMemoryCorpusRepository repository = new InMemoryCorpusRepository(corpus);
        ValidateCorpus validateCorpus = new ValidateCorpus(repository);

        Corpus result = validateCorpus.validateIds(Set.of("d01"));

        assertThat(byId(result, "d01").manuallyValidated()).isTrue();
        assertThat(byId(result, "d02").manuallyValidated()).isFalse();
        assertThat(byId(result, "d03").manuallyValidated()).isFalse();
        assertThat(repository.saved()).isEqualTo(result);
    }

    @Test
    void validateIdsFreezesTheAbstractShaOfTheValidatedDocument() {
        Corpus corpus = corpusOf(unvalidated("d01"));
        ValidateCorpus validateCorpus = new ValidateCorpus(new InMemoryCorpusRepository(corpus));

        Corpus result = validateCorpus.validateIds(Set.of("d01"));

        assertThat(byId(result, "d01").abstractSha256())
                .isEqualTo(CorpusHasher.abstractSha256(byId(result, "d01").abstractText()));
    }

    @Test
    void validateIdsRecomputesCorpusSha256() {
        Corpus corpus = corpusOf(unvalidated("d01"), unvalidated("d02"));
        ValidateCorpus validateCorpus = new ValidateCorpus(new InMemoryCorpusRepository(corpus));

        Corpus result = validateCorpus.validateIds(Set.of("d01"));

        assertThat(result.corpusSha256()).isEqualTo(CorpusHasher.corpusSha256(result.documents()));
    }

    @Test
    void validateAllMarksEveryDocumentAsManuallyValidated() {
        Corpus corpus = corpusOf(unvalidated("d01"), unvalidated("d02"));
        ValidateCorpus validateCorpus = new ValidateCorpus(new InMemoryCorpusRepository(corpus));

        Corpus result = validateCorpus.validateAll();

        assertThat(result.documents()).allSatisfy(doc -> assertThat(doc.manuallyValidated()).isTrue());
    }

    @Test
    void rejectsAnUnknownDocumentId() {
        Corpus corpus = corpusOf(unvalidated("d01"));
        ValidateCorpus validateCorpus = new ValidateCorpus(new InMemoryCorpusRepository(corpus));

        assertThatIllegalArgumentException().isThrownBy(() -> validateCorpus.validateIds(Set.of("d99")));
    }

    @Test
    void rejectsAnEmptyIdSet() {
        Corpus corpus = corpusOf(unvalidated("d01"));
        ValidateCorpus validateCorpus = new ValidateCorpus(new InMemoryCorpusRepository(corpus));

        assertThatIllegalArgumentException().isThrownBy(() -> validateCorpus.validateIds(Set.of()));
    }

    private static CorpusDocument byId(Corpus corpus, String id) {
        return corpus.documents().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow();
    }

    private static CorpusDocument unvalidated(String id) {
        String abstractText = "abstract text for " + id + " with enough real words";
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText, "data/pdfs/" + id + ".pdf",
                "GROBID", false, CorpusHasher.abstractSha256(abstractText));
    }

    private static Corpus corpusOf(CorpusDocument... documents) {
        List<CorpusDocument> docs = List.of(documents);
        return new Corpus("1.0", docs.size(), CorpusHasher.corpusSha256(docs), docs);
    }
}
