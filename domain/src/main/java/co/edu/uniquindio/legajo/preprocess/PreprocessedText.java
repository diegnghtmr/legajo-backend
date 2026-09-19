package co.edu.uniquindio.legajo.preprocess;

import java.util.List;
import java.util.Objects;

/**
 * Immutable output of {@link TextPreprocessor}: the final token stream and whether
 * Porter stemming (TRD §6.2, step 5) was applied, so downstream traces can report the
 * {@code preprocess.stemming} flag alongside the tokens it produced.
 */
public record PreprocessedText(List<String> tokens, boolean stemmingApplied) {

    public PreprocessedText {
        Objects.requireNonNull(tokens, "tokens");
        tokens = List.copyOf(tokens);
    }
}
