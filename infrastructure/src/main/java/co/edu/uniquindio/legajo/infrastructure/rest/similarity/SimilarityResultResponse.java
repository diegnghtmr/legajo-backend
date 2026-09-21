package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.SimilarityResult;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Wire shape of one similarity result within {@code POST /similarity/compare} and
 * {@code POST /similarity/matrix} (TRD §6.6): {@code normalizedScore}, {@code rawValue}
 * (nullable), {@code computedNanos}, {@code cached}, and {@code degenerate}.
 *
 * <p><b>{@code cached} is always {@code false} in A3.</b> The domain's {@link
 * SimilarityResult} (task A2's {@code SimilarityService} orchestrates over it) has no
 * {@code cached} field — every result A2 returns is freshly computed, and request-keyed
 * caching is a separate feature task, A5 (see {@code SimilarityService}'s own Javadoc on
 * this exact gap). Rather than inventing a cache here or reshaping the domain/application
 * layer to carry a flag nothing yet sets, this DTO adds the field at the REST boundary with
 * a fixed value: every response over this A3 slice reports {@code cached: false},
 * truthfully, because nothing caches yet. A5 is expected to make this field real by
 * threading an actual cache-hit flag through to this constructor (or an equivalent DTO
 * factory), not by touching {@link SimilarityResult}.
 */
public record SimilarityResultResponse(
        double normalizedScore, @Nullable Double rawValue, long computedNanos, boolean cached, boolean degenerate) {

    public static SimilarityResultResponse from(SimilarityResult result) {
        Objects.requireNonNull(result, "result");
        return new SimilarityResultResponse(
                result.normalizedScore(), result.rawValue(), result.computedNanos(), false, result.degenerate());
    }
}
