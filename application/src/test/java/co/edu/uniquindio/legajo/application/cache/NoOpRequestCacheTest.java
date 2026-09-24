package co.edu.uniquindio.legajo.application.cache;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The similarity/clustering benchmarks
 * run with the similarity cache disabled. {@link NoOpRequestCache} is the
 * constructor-injectable substitute for {@code CaffeineRequestCache} that makes that
 * possible — it must never report a hit, even for a key it was just given a value for.
 */
class NoOpRequestCacheTest {

    private final NoOpRequestCache<String, String> cache = new NoOpRequestCache<>();

    @Test
    void getIsAlwaysEmptyBeforeAnyPut() {
        Optional<String> result = cache.get("key-1");

        assertThat(result).isEmpty();
    }

    @Test
    void getIsStillEmptyAfterAPutForTheSameKey() {
        cache.put("key-1", "value-1");

        Optional<String> result = cache.get("key-1");

        assertThat(result).as("a no-op cache must never turn a put into a later hit").isEmpty();
    }
}
