package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.application.similarity.CachedSimilarityResult;
import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Wire shape of one similarity result within {@code POST /similarity/compare} and
 * {@code POST /similarity/matrix}: {@code normalizedScore}, {@code rawValue}
 * (nullable), {@code computedNanos}, {@code cached}, {@code degenerate}, and {@code stemming}
 * (whether the classic tokens were Porter-stemmed).
 *
 * <p><b>{@code cached} is real.</b> {@code SimilarityService} now looks up a
 * request-keyed cache before computing; {@link #from(SimilarityResult, boolean)}
 * threads that hit/miss flag straight through from the application layer, and {@link
 * #from(CachedSimilarityResult)} does the same for a {@code POST /similarity/matrix} cell,
 * which carries the same flag under a different application-layer shape (no
 * {@code algorithmId} of its own).
 */
public record SimilarityResultResponse(
        double normalizedScore, @Nullable Double rawValue, long computedNanos, boolean cached, boolean degenerate,
        boolean stemming) {

    public static SimilarityResultResponse from(SimilarityResult result, boolean cached, boolean stemming) {
        Objects.requireNonNull(result, "result");
        return new SimilarityResultResponse(result.normalizedScore(), result.rawValue(), result.computedNanos(),
                cached, result.degenerate(), stemming);
    }

    public static SimilarityResultResponse from(CachedSimilarityResult cell, boolean stemming) {
        Objects.requireNonNull(cell, "cell");
        return from(cell.result(), cell.cached(), stemming);
    }
}
