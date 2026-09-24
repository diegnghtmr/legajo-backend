package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.port.CorpusRepository;

import java.util.NoSuchElementException;

/**
 * In-memory {@link CorpusRepository} test double shared by the ingestion use case
 * tests, standing in for {@code JsonCorpusRepository} (an infrastructure concern) so
 * {@code application} tests never depend on infrastructure or the filesystem.
 */
final class InMemoryCorpusRepository implements CorpusRepository {

    private Corpus corpus;

    InMemoryCorpusRepository() {
    }

    InMemoryCorpusRepository(Corpus initial) {
        this.corpus = initial;
    }

    @Override
    public Corpus load() {
        if (corpus == null) {
            throw new NoSuchElementException("no corpus has been saved yet");
        }
        return corpus;
    }

    @Override
    public void save(Corpus corpus) {
        this.corpus = corpus;
    }

    Corpus saved() {
        return corpus;
    }
}
