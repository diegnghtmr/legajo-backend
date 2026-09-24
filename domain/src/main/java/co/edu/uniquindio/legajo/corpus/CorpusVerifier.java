package co.edu.uniquindio.legajo.corpus;

import co.edu.uniquindio.legajo.preprocess.TextPreprocessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * The {@code verify-corpus} rules, plus a corpus-wide hash
 * cross-check against the fixed {@code corpusSha256} definition: every rule is
 * independently evaluated and every violation is collected, so a single run reports
 * everything wrong with a corpus rather than stopping at the first problem.
 *
 * <p>The "no empty preprocessed token stream" rule only runs for a document whose
 * abstract is non-blank: a blank abstract is already reported by
 * {@link CorpusRule#NON_BLANK_ABSTRACT}, and re-reporting it as an empty token stream
 * would just restate the same root cause under a second rule. The rule still fires on
 * its own for a non-blank abstract that reduces to zero tokens (e.g. one made
 * entirely of stopwords), which is the case this guards against.
 */
public final class CorpusVerifier {

    private final TextPreprocessor textPreprocessor;

    public CorpusVerifier() {
        this(new TextPreprocessor());
    }

    public CorpusVerifier(TextPreprocessor textPreprocessor) {
        this.textPreprocessor = Objects.requireNonNull(textPreprocessor, "textPreprocessor");
    }

    public CorpusVerificationResult verify(Corpus corpus) {
        return verify(corpus, OptionalInt.empty());
    }

    public CorpusVerificationResult verify(Corpus corpus, OptionalInt expectedDocumentCount) {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(expectedDocumentCount, "expectedDocumentCount");

        List<CorpusDocument> documents = corpus.documents();
        List<CorpusViolation> violations = new ArrayList<>();

        checkDocumentCount(corpus, documents, expectedDocumentCount, violations);
        checkMinimumSize(documents, violations);
        checkUniqueDocumentIds(documents, violations);
        checkNoDuplicateAbstractHashes(documents, violations);
        documents.forEach(document -> checkDocument(document, violations));
        checkCorpusHash(corpus, documents, violations);

        return new CorpusVerificationResult(violations);
    }

    private void checkDocumentCount(Corpus corpus, List<CorpusDocument> documents,
            OptionalInt expectedDocumentCount, List<CorpusViolation> violations) {
        if (documents.size() != corpus.sourceCount()) {
            violations.add(CorpusViolation.forCorpus(CorpusRule.DOCUMENT_COUNT_MATCHES_SOURCE_COUNT,
                    "expected %d documents (sourceCount) but found %d".formatted(corpus.sourceCount(), documents.size())));
        }
        if (expectedDocumentCount.isPresent() && documents.size() != expectedDocumentCount.getAsInt()) {
            violations.add(CorpusViolation.forCorpus(CorpusRule.DOCUMENT_COUNT_MATCHES_EXPECTED,
                    "expected %d documents but found %d".formatted(expectedDocumentCount.getAsInt(), documents.size())));
        }
    }

    private void checkMinimumSize(List<CorpusDocument> documents, List<CorpusViolation> violations) {
        if (documents.size() < 3) {
            violations.add(CorpusViolation.forCorpus(CorpusRule.MINIMUM_DOCUMENT_COUNT,
                    "corpus must have at least 3 documents (n >= 3) but has %d".formatted(documents.size())));
        }
    }

    private void checkUniqueDocumentIds(List<CorpusDocument> documents, List<CorpusViolation> violations) {
        Map<String, List<CorpusDocument>> documentsById = documents.stream()
                .collect(Collectors.groupingBy(CorpusDocument::id, LinkedHashMap::new, Collectors.toList()));

        documentsById.forEach((id, docs) -> {
            if (docs.size() > 1) {
                docs.forEach(doc -> violations.add(CorpusViolation.forDocument(CorpusRule.UNIQUE_DOCUMENT_ID, id,
                        "id %s is shared with %d other document(s)".formatted(id, docs.size() - 1))));
            }
        });
    }

    private void checkNoDuplicateAbstractHashes(List<CorpusDocument> documents, List<CorpusViolation> violations) {
        Map<String, List<String>> idsByAbstractSha = documents.stream()
                .collect(Collectors.groupingBy(CorpusDocument::abstractSha256, LinkedHashMap::new,
                        Collectors.mapping(CorpusDocument::id, Collectors.toList())));

        idsByAbstractSha.forEach((sha, ids) -> {
            if (ids.size() > 1) {
                ids.forEach(id -> violations.add(CorpusViolation.forDocument(CorpusRule.NO_DUPLICATE_ABSTRACT_SHA256, id,
                        "abstractSha256 %s is shared with %d other document(s): %s".formatted(sha, ids.size() - 1, ids))));
            }
        });
    }

    private void checkDocument(CorpusDocument document, List<CorpusViolation> violations) {
        if (document.title() == null || document.title().isBlank()) {
            violations.add(CorpusViolation.forDocument(CorpusRule.NON_BLANK_TITLE, document.id(), "title must not be blank"));
        }
        if (document.authors().isEmpty()) {
            violations.add(CorpusViolation.forDocument(CorpusRule.NON_EMPTY_AUTHORS, document.id(), "authors must not be empty"));
        }
        if (document.abstractText() == null || document.abstractText().isBlank()) {
            violations.add(CorpusViolation.forDocument(CorpusRule.NON_BLANK_ABSTRACT, document.id(), "abstract must not be blank"));
        } else {
            checkPreprocessedTokens(document, violations);
            checkAbstractHash(document, violations);
        }
        if (!document.manuallyValidated()) {
            violations.add(CorpusViolation.forDocument(CorpusRule.MANUALLY_VALIDATED, document.id(), "manuallyValidated must be true"));
        }
    }

    private void checkPreprocessedTokens(CorpusDocument document, List<CorpusViolation> violations) {
        List<String> tokens = textPreprocessor.preprocess(document.abstractText()).tokens();
        if (tokens.isEmpty()) {
            violations.add(CorpusViolation.forDocument(CorpusRule.NON_EMPTY_PREPROCESSED_TOKENS, document.id(),
                    "preprocessed token stream is empty"));
        }
    }

    private void checkAbstractHash(CorpusDocument document, List<CorpusViolation> violations) {
        String recomputed = CorpusHasher.abstractSha256(document.abstractText());
        if (!recomputed.equals(document.abstractSha256())) {
            violations.add(CorpusViolation.forDocument(CorpusRule.ABSTRACT_SHA256_MATCHES_RECOMPUTED, document.id(),
                    "recomputed abstractSha256 %s does not match frozen value %s".formatted(recomputed, document.abstractSha256())));
        }
    }

    private void checkCorpusHash(Corpus corpus, List<CorpusDocument> documents, List<CorpusViolation> violations) {
        String recomputed = CorpusHasher.corpusSha256(documents);
        if (!recomputed.equals(corpus.corpusSha256())) {
            violations.add(CorpusViolation.forCorpus(CorpusRule.CORPUS_SHA256_MATCHES_RECOMPUTED,
                    "recomputed corpusSha256 %s does not match frozen value %s".formatted(recomputed, corpus.corpusSha256())));
        }
    }
}
