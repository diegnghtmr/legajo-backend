package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.port.CorpusRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The manual-validation use case — "the author reviews each
 * abstract" is the only mandatory control, and this is the only place {@code
 * manuallyValidated} is ever set to {@code true}: always through an explicit call
 * naming ids ({@link #validateIds(Set)}) or every document ({@link #validateAll()}),
 * never automatically as a side effect of ingestion or verification (this field is
 * never set by the agent on its own).
 *
 * <p>Validating a document also (re)freezes its {@code abstractSha256} to the sha256 of
 * its current {@code abstractText} and recomputes {@code corpusSha256} for the whole
 * corpus, per the frozen-hash definition.
 */
public final class ValidateCorpus {

    private final CorpusRepository repository;

    public ValidateCorpus(CorpusRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Corpus validateAll() {
        Corpus corpus = repository.load();
        Set<String> allIds = corpus.documents().stream().map(CorpusDocument::id).collect(Collectors.toSet());
        return freezeAndSave(corpus, allIds);
    }

    public Corpus validateIds(Set<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty; use validateAll() to validate every document");
        }

        Corpus corpus = repository.load();
        Set<String> knownIds = corpus.documents().stream().map(CorpusDocument::id).collect(Collectors.toSet());
        Set<String> unknownIds = new HashSet<>(ids);
        unknownIds.removeAll(knownIds);
        if (!unknownIds.isEmpty()) {
            throw new IllegalArgumentException("Unknown document id(s): " + unknownIds);
        }

        return freezeAndSave(corpus, ids);
    }

    private Corpus freezeAndSave(Corpus corpus, Set<String> targetIds) {
        List<CorpusDocument> updatedDocuments = corpus.documents().stream()
                .map(document -> targetIds.contains(document.id()) ? freeze(document) : document)
                .toList();
        Corpus updatedCorpus = new Corpus(corpus.version(), corpus.sourceCount(),
                CorpusHasher.corpusSha256(updatedDocuments), updatedDocuments);
        repository.save(updatedCorpus);
        return updatedCorpus;
    }

    private static CorpusDocument freeze(CorpusDocument document) {
        return new CorpusDocument(document.id(), document.title(), document.authors(), document.abstractText(),
                document.source(), document.extractedBy(), true, CorpusHasher.abstractSha256(document.abstractText()));
    }
}
