package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §14.1 / feature doc: {@code legajo.embedding-provider} and {@code legajo.cors-origins}
 * are declared in {@code application.yml} but read by nothing today (dead configuration,
 * same shape as {@code LEGAJO_GROBID_URL}). This is the first {@code @ConfigurationProperties}
 * in the project; these tests prove the two keys are genuinely bound, with the exact
 * defaults {@code application.yml}'s placeholders already promise
 * ({@code ${LEGAJO_EMBEDDING_PROVIDER:cached}}, {@code ${LEGAJO_CORS_ORIGINS:}}).
 */
class LegajoPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @EnableConfigurationProperties(LegajoProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsToCachedAndNoOriginsWhenNeitherKeyIsSet() {
        contextRunner.run(context -> {
            LegajoProperties properties = context.getBean(LegajoProperties.class);
            assertThat(properties.embeddingProvider()).isEqualTo(EmbeddingProviderMode.CACHED);
            assertThat(properties.corsOrigins()).isEmpty();
        });
    }

    @Test
    void bindsAnEmptyCorsOriginsStringToNoOrigins() {
        contextRunner.withPropertyValues("legajo.cors-origins=").run(context -> {
            LegajoProperties properties = context.getBean(LegajoProperties.class);
            assertThat(properties.corsOrigins()).isEmpty();
        });
    }

    @Test
    void bindsACommaDelimitedCorsOriginsStringToAnOrderedList() {
        contextRunner
                .withPropertyValues("legajo.cors-origins=http://localhost:5173,https://legajo.example.com")
                .run(context -> {
                    LegajoProperties properties = context.getBean(LegajoProperties.class);
                    assertThat(properties.corsOrigins())
                            .containsExactly("http://localhost:5173", "https://legajo.example.com");
                });
    }

    @Test
    void bindsLiveEmbeddingProvider() {
        contextRunner.withPropertyValues("legajo.embedding-provider=live").run(context -> {
            LegajoProperties properties = context.getBean(LegajoProperties.class);
            assertThat(properties.embeddingProvider()).isEqualTo(EmbeddingProviderMode.LIVE);
        });
    }

    @Test
    void failsClosedOnAnUnknownEmbeddingProviderValue() {
        contextRunner.withPropertyValues("legajo.embedding-provider=bogus").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }
}
