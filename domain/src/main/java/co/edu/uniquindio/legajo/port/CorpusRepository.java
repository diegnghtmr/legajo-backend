package co.edu.uniquindio.legajo.port;

import co.edu.uniquindio.legajo.corpus.Corpus;

/**
 * Output port for corpus persistence (TRD §9): {@code domain} depends only on this
 * interface. The JSON adapter that reads and writes {@code data/corpus.json} is an
 * infrastructure concern (task T4), not part of this module.
 */
public interface CorpusRepository {

    /** Loads the corpus currently on disk. */
    Corpus load();

    /** Replaces the corpus on disk with {@code corpus}, following TRD §9's "generated, not edited" rule. */
    void save(Corpus corpus);
}
