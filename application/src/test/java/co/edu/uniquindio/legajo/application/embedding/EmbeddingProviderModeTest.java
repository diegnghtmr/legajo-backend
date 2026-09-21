package co.edu.uniquindio.legajo.application.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TRD §14.1: the default profile runs {@code cached}, live mode is {@code live}. Mirrors the
 * {@code id()}/{@code fromId(String)} convention {@code Representation} already uses, so
 * {@code legajo.embedding-provider}'s raw string value round-trips the same way.
 */
class EmbeddingProviderModeTest {

    @Test
    void defaultIsCached() {
        assertThat(EmbeddingProviderMode.DEFAULT).isEqualTo(EmbeddingProviderMode.CACHED);
    }

    @Test
    void idsAreStableAndHyphenated() {
        assertThat(EmbeddingProviderMode.CACHED.id()).isEqualTo("cached");
        assertThat(EmbeddingProviderMode.LIVE.id()).isEqualTo("live");
    }

    @Test
    void fromIdResolvesTheMatchingMode() {
        assertThat(EmbeddingProviderMode.fromId("cached")).isEqualTo(EmbeddingProviderMode.CACHED);
        assertThat(EmbeddingProviderMode.fromId("live")).isEqualTo(EmbeddingProviderMode.LIVE);
    }

    @Test
    void fromIdRejectsAnUnknownValue() {
        assertThatThrownBy(() -> EmbeddingProviderMode.fromId("bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bogus");
    }
}
