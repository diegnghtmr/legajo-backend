package co.edu.uniquindio.legajo.corpus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verify-corpus rules: every rule below must be checked independently
 * and every violation reported, never fail-fast on the first one.
 */
class CorpusVerifierTest {

    private final CorpusVerifier verifier = new CorpusVerifier();

    private static CorpusDocument validDocument(String id, String abstractText) {
        return new CorpusDocument(id, "Title " + id, List.of("Author One"), abstractText,
                "data/pdfs/" + id + ".pdf", "GROBID", true, CorpusHasher.abstractSha256(abstractText));
    }

    private static Corpus corpusOf(CorpusDocument... documents) {
        List<CorpusDocument> docs = List.of(documents);
        return new Corpus("1.0", docs.size(), CorpusHasher.corpusSha256(docs), docs);
    }

    @Test
    void aFullyValidCorpusHasNoViolations() {
        Corpus corpus = corpusOf(
                validDocument("d01", "This paper studies clustering of scientific abstracts."),
                validDocument("d02", "A survey of similarity measures for short text."),
                validDocument("d03", "We propose a hierarchical agglomerative clustering method."));

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.isValid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void reportsWhenDocumentCountDoesNotMatchSourceCount() {
        List<CorpusDocument> docs = List.of(
                validDocument("d01", "abstract one has enough words"),
                validDocument("d02", "abstract two has enough words"),
                validDocument("d03", "abstract three has enough words"));
        Corpus corpus = new Corpus("1.0", 5, CorpusHasher.corpusSha256(docs), docs);

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> {
                    assertThat(v.rule()).isEqualTo(CorpusRule.DOCUMENT_COUNT_MATCHES_SOURCE_COUNT);
                    assertThat(v.isCorpusLevel()).isTrue();
                });
    }

    @Test
    void reportsWhenDocumentCountDoesNotMatchExplicitExpectedCount() {
        Corpus corpus = corpusOf(
                validDocument("d01", "abstract one has enough words"),
                validDocument("d02", "abstract two has enough words"),
                validDocument("d03", "abstract three has enough words"));

        CorpusVerificationResult result = verifier.verify(corpus, OptionalInt.of(20));

        assertThat(result.violations())
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.DOCUMENT_COUNT_MATCHES_EXPECTED));
    }

    @Test
    void reportsWhenFewerThanThreeDocuments() {
        Corpus corpus = corpusOf(
                validDocument("d01", "abstract one has enough words"),
                validDocument("d02", "abstract two has enough words"));

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.MINIMUM_DOCUMENT_COUNT));
    }

    @Test
    void reportsDuplicateAbstractSha256OnBothDocuments() {
        CorpusDocument doc1 = validDocument("d01", "identical abstract text for duplicate test");
        CorpusDocument duplicate = new CorpusDocument("d02", "Title d02", List.of("Author"),
                "identical abstract text for duplicate test", "data/pdfs/d02.pdf", "GROBID", true,
                doc1.abstractSha256());
        CorpusDocument other = validDocument("d03", "a distinct third abstract with different words");
        Corpus corpus = corpusOf(doc1, duplicate, other);

        CorpusVerificationResult result = verifier.verify(corpus);

        List<CorpusViolation> duplicateViolations = result.violations().stream()
                .filter(v -> v.rule() == CorpusRule.NO_DUPLICATE_ABSTRACT_SHA256)
                .toList();
        assertThat(duplicateViolations).extracting(CorpusViolation::documentId)
                .containsExactlyInAnyOrder("d01", "d02");
    }

    @Test
    void reportsDuplicateDocumentIdsBecauseTheyMakeCorpusSha256OrderAmbiguous() {
        CorpusDocument first = validDocument("d01", "the first abstract with this shared id");
        CorpusDocument duplicateId = new CorpusDocument("d01", "Title d01 duplicate", List.of("Author"),
                "a completely different abstract text", "data/pdfs/d01.pdf", "GROBID", true,
                CorpusHasher.abstractSha256("a completely different abstract text"));
        CorpusDocument other = validDocument("d03", "a distinct third abstract with different words");
        Corpus corpus = corpusOf(first, duplicateId, other);

        CorpusVerificationResult result = verifier.verify(corpus);

        List<CorpusViolation> duplicateIdViolations = result.violations().stream()
                .filter(v -> v.rule() == CorpusRule.UNIQUE_DOCUMENT_ID)
                .toList();
        assertThat(duplicateIdViolations).extracting(CorpusViolation::documentId)
                .containsExactlyInAnyOrder("d01", "d01");
    }

    @Test
    void reportsBlankTitleAuthorsAndAbstractIndependently() {
        CorpusDocument blankTitle = new CorpusDocument("d01", "", List.of("Author"), "a valid abstract text here",
                "data/pdfs/d01.pdf", "GROBID", true, CorpusHasher.abstractSha256("a valid abstract text here"));
        CorpusDocument noAuthors = new CorpusDocument("d02", "Title", List.of(), "another valid abstract here",
                "data/pdfs/d02.pdf", "GROBID", true, CorpusHasher.abstractSha256("another valid abstract here"));
        CorpusDocument blankAbstract = new CorpusDocument("d03", "Title", List.of("Author"), "",
                "data/pdfs/d03.pdf", "GROBID", true, CorpusHasher.abstractSha256(""));
        Corpus corpus = corpusOf(blankTitle, noAuthors, blankAbstract);

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.NON_BLANK_TITLE))
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.NON_EMPTY_AUTHORS))
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo(CorpusRule.NON_BLANK_ABSTRACT));
    }

    @Test
    void reportsEmptyPreprocessedTokenStreamForANonBlankAllStopwordAbstract() {
        String allStopwords = "the a an";
        CorpusDocument document = new CorpusDocument("d01", "Title", List.of("Author"), allStopwords,
                "data/pdfs/d01.pdf", "GROBID", true, CorpusHasher.abstractSha256(allStopwords));
        Corpus corpus = corpusOf(
                document,
                validDocument("d02", "abstract two has enough real words"),
                validDocument("d03", "abstract three has enough real words"));

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> {
                    assertThat(v.rule()).isEqualTo(CorpusRule.NON_EMPTY_PREPROCESSED_TOKENS);
                    assertThat(v.documentId()).isEqualTo("d01");
                });
    }

    @Test
    void reportsWhenNotManuallyValidated() {
        CorpusDocument notValidated = new CorpusDocument("d01", "Title", List.of("Author"),
                "a valid abstract text here", "data/pdfs/d01.pdf", "GROBID", false,
                CorpusHasher.abstractSha256("a valid abstract text here"));
        Corpus corpus = corpusOf(
                notValidated,
                validDocument("d02", "abstract two has enough real words"),
                validDocument("d03", "abstract three has enough real words"));

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> {
                    assertThat(v.rule()).isEqualTo(CorpusRule.MANUALLY_VALIDATED);
                    assertThat(v.documentId()).isEqualTo("d01");
                });
    }

    @Test
    void reportsWhenFrozenAbstractShaDoesNotMatchRecomputedValue() {
        CorpusDocument tampered = new CorpusDocument("d01", "Title", List.of("Author"),
                "a valid abstract text here", "data/pdfs/d01.pdf", "GROBID", true, "not-a-real-sha256");
        Corpus corpus = corpusOf(
                tampered,
                validDocument("d02", "abstract two has enough real words"),
                validDocument("d03", "abstract three has enough real words"));

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> {
                    assertThat(v.rule()).isEqualTo(CorpusRule.ABSTRACT_SHA256_MATCHES_RECOMPUTED);
                    assertThat(v.documentId()).isEqualTo("d01");
                });
    }

    @Test
    void reportsWhenFrozenCorpusShaDoesNotMatchRecomputedValue() {
        List<CorpusDocument> docs = List.of(
                validDocument("d01", "abstract one has enough words"),
                validDocument("d02", "abstract two has enough words"),
                validDocument("d03", "abstract three has enough words"));
        Corpus corpus = new Corpus("1.0", docs.size(), "0000000000000000000000000000000000000000000000000000000000000000", docs);

        CorpusVerificationResult result = verifier.verify(corpus);

        assertThat(result.violations())
                .anySatisfy(v -> {
                    assertThat(v.rule()).isEqualTo(CorpusRule.CORPUS_SHA256_MATCHES_RECOMPUTED);
                    assertThat(v.isCorpusLevel()).isTrue();
                });
    }

    @Test
    void reportsEveryViolationInASinglePassRatherThanFailingFast() {
        CorpusDocument broken = new CorpusDocument("d01", "", List.of(), "", "data/pdfs/d01.pdf", "GROBID", false,
                "wrong-sha");
        Corpus corpus = new Corpus("1.0", 3, "wrong-corpus-sha", List.of(broken));

        CorpusVerificationResult result = verifier.verify(corpus, OptionalInt.of(20));

        List<CorpusRule> rulesViolated = result.violations().stream().map(CorpusViolation::rule).toList();
        assertThat(rulesViolated).contains(
                CorpusRule.DOCUMENT_COUNT_MATCHES_SOURCE_COUNT,
                CorpusRule.DOCUMENT_COUNT_MATCHES_EXPECTED,
                CorpusRule.MINIMUM_DOCUMENT_COUNT,
                CorpusRule.NON_BLANK_TITLE,
                CorpusRule.NON_EMPTY_AUTHORS,
                CorpusRule.NON_BLANK_ABSTRACT,
                CorpusRule.MANUALLY_VALIDATED,
                CorpusRule.CORPUS_SHA256_MATCHES_RECOMPUTED);
    }
}
