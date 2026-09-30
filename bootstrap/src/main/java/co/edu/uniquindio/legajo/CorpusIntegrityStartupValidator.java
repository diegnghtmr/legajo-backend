package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.port.CorpusRepository;

import java.util.Objects;

/**
 * Fails startup when the {@code corpusSha256} stored in {@code corpus.json} is not the hash of
 * the documents actually loaded, so a hand-edited or half-updated corpus can never be served
 * (and can never silently re-bind the embedding caches to a corpus they were not computed for).
 */
final class CorpusIntegrityStartupValidator {

    private final CorpusRepository corpusRepository;

    CorpusIntegrityStartupValidator(CorpusRepository corpusRepository) {
        this.corpusRepository = Objects.requireNonNull(corpusRepository, "corpusRepository");
    }

    void validate() {
        Corpus corpus = corpusRepository.load();
        String recomputed = CorpusHasher.corpusSha256(corpus.documents());
        if (!recomputed.equals(corpus.corpusSha256())) {
            throw new IllegalStateException(
                    ("corpus.json is inconsistent: stored corpusSha256=%s but the loaded documents hash to %s. "
                            + "Inspect it with ./gradlew :bootstrap:verifyCorpus and re-run the ingestion "
                            + "before starting the server.").formatted(corpus.corpusSha256(), recomputed));
        }
    }
}
