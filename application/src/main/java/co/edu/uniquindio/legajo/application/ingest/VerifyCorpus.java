package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.CorpusVerificationResult;
import co.edu.uniquindio.legajo.corpus.CorpusVerifier;
import co.edu.uniquindio.legajo.port.CorpusRepository;

import java.util.Objects;

/**
 * The {@code verify-corpus} use case (TRD §6.1, item 6): load the corpus through the
 * port and run every {@link CorpusVerifier} rule against it, reporting every violation
 * rather than stopping at the first one.
 */
public final class VerifyCorpus {

    private final CorpusRepository repository;
    private final CorpusVerifier verifier;

    public VerifyCorpus(CorpusRepository repository) {
        this(repository, new CorpusVerifier());
    }

    public VerifyCorpus(CorpusRepository repository, CorpusVerifier verifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public CorpusVerificationResult verify() {
        return verifier.verify(repository.load());
    }
}
