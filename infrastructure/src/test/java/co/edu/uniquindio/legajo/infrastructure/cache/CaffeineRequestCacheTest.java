package co.edu.uniquindio.legajo.infrastructure.cache;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The infrastructure implementation of {@code RequestCache}: a Caffeine
 * region with no TTL — the corpus is static, so nothing here ever expires by time.
 */
class CaffeineRequestCacheTest {

    private final CaffeineRequestCache<String, String> cache = new CaffeineRequestCache<>();

    @Test
    void getIsEmptyForAKeyNeverPut() {
        assertThat(cache.get("missing")).isEmpty();
    }

    @Test
    void getReturnsExactlyWhatWasPutForThatKey() {
        cache.put("key-1", "value-1");

        Optional<String> result = cache.get("key-1");

        assertThat(result).contains("value-1");
    }

    @Test
    void putReplacesAnyPreviousValueForTheSameKey() {
        cache.put("key-1", "first");
        cache.put("key-1", "second");

        assertThat(cache.get("key-1")).contains("second");
    }

    @Test
    void differentKeysAreIndependent() {
        cache.put("key-1", "value-1");

        assertThat(cache.get("key-2")).isEmpty();
    }
}
