/**
 * Text preprocessing pipeline: NFC normalization, lowercasing, tokenization, stopword
 * removal, and optional Porter stemming (TRD §6.2). {@link TextPreprocessor} is the
 * entry point; {@link PorterStemmer} is its package-private, hand-written Porter
 * (1980) stemmer, used only when {@code preprocess.stemming} is enabled.
 */
@NullMarked
package co.edu.uniquindio.legajo.preprocess;

import org.jspecify.annotations.NullMarked;
