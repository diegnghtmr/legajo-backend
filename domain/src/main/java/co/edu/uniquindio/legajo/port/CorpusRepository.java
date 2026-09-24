package co.edu.uniquindio.legajo.port;

import co.edu.uniquindio.legajo.corpus.Corpus;

/**
 * Output port for corpus persistence: {@code domain} depends only on this
 * interface. The JSON adapter that reads and writes {@code data/corpus.json} is an
 * infrastructure concern, not part of this module.
 */
public interface CorpusRepository {

    /** Loads the corpus currently on disk. */
    Corpus load();

    /** Replaces the corpus on disk with {@code corpus}: data is generated, never edited by hand. */
    void save(Corpus corpus);
}
