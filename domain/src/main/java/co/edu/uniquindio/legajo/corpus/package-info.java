/**
 * Corpus domain model (TRD §9): {@link co.edu.uniquindio.legajo.corpus.Corpus} and
 * {@link co.edu.uniquindio.legajo.corpus.CorpusDocument}, sha256 hashing rules
 * ({@link co.edu.uniquindio.legajo.corpus.CorpusHasher}, TRD §6.1), and the
 * {@code verify-corpus} rules ({@link co.edu.uniquindio.legajo.corpus.CorpusVerifier}).
 * Pure Java: no JSON, no PDF parsing, no framework — those are infrastructure
 * concerns (task T4).
 */
@NullMarked
package co.edu.uniquindio.legajo.corpus;

import org.jspecify.annotations.NullMarked;
