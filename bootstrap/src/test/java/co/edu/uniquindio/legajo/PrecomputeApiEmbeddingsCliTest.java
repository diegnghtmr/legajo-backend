package co.edu.uniquindio.legajo;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * {@code precomputeApiEmbeddings}'s argument and fail-closed contract.
 * The live network call in {@code main} needs a real OpenAI-compatible
 * endpoint, so this exercises {@link
 * PrecomputeApiEmbeddingsCli#resolveConfig(String[], PrintStream, Function)} —
 * everything that must happen before that call — against an injected fake environment,
 * with no real environment variables read or mutated.
 */
class PrecomputeApiEmbeddingsCliTest {

    private static final Map<String, String> COMPLETE_ENVIRONMENT = Map.of(
            "SPRING_AI_OPENAI_API_KEY", "test-key",
            "SPRING_AI_OPENAI_BASE_URL", "https://example.invalid/v1beta/openai",
            "LEGAJO_EMBEDDING_API_MODEL", "gemini-embedding-2-preview",
            "LEGAJO_EMBEDDING_API_DIMENSION", "1536");

    @Test
    void resolvesDefaultsAndTheCompleteEnvironment() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

        PrecomputeApiEmbeddingsCli.Config config = PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), COMPLETE_ENVIRONMENT::get);

        assertThat(config.corpusPath()).isEqualTo("data/corpus.json");
        assertThat(config.outputPath()).isEqualTo("data/embeddings-openai.json");
        assertThat(config.apiKey()).isEqualTo("test-key");
        assertThat(config.baseUrl()).isEqualTo("https://example.invalid/v1beta/openai");
        assertThat(config.model()).isEqualTo("gemini-embedding-2-preview");
        assertThat(config.dimension()).isEqualTo(1536);
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void resolveConfigHonorsCorpusAndOutputOverrides() {
        PrecomputeApiEmbeddingsCli.Config config = PrecomputeApiEmbeddingsCli.resolveConfig(
                new String[] {"--corpus=custom/corpus.json", "--output=custom/embeddings.json"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), COMPLETE_ENVIRONMENT::get);

        assertThat(config.corpusPath()).isEqualTo("custom/corpus.json");
        assertThat(config.outputPath()).isEqualTo("custom/embeddings.json");
    }

    @Test
    void failsClosedWhenARequiredEnvironmentVariableIsMissing() {
        Function<String, String> missingApiKey = name -> "SPRING_AI_OPENAI_API_KEY".equals(name)
                ? null
                : COMPLETE_ENVIRONMENT.get(name);

        assertThatIllegalStateException()
                .isThrownBy(() -> PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), missingApiKey))
                .withMessageContaining("SPRING_AI_OPENAI_API_KEY");
    }

    @Test
    void failsClosedWhenARequiredEnvironmentVariableIsBlank() {
        Function<String, String> blankBaseUrl = name -> "SPRING_AI_OPENAI_BASE_URL".equals(name)
                ? "   "
                : COMPLETE_ENVIRONMENT.get(name);

        assertThatIllegalStateException()
                .isThrownBy(() -> PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), blankBaseUrl))
                .withMessageContaining("SPRING_AI_OPENAI_BASE_URL");
    }

    @Test
    void failsClosedWhenTheDimensionIsNotAnInteger() {
        Function<String, String> invalidDimension = name -> "LEGAJO_EMBEDDING_API_DIMENSION".equals(name)
                ? "not-a-number"
                : COMPLETE_ENVIRONMENT.get(name);

        assertThatIllegalStateException()
                .isThrownBy(() -> PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), invalidDimension))
                .withMessageContaining("LEGAJO_EMBEDDING_API_DIMENSION")
                .withMessageContaining("not-a-number");
    }

    @Test
    void failsClosedWhenTheDimensionIsNotPositive() {
        Function<String, String> zeroDimension = name -> "LEGAJO_EMBEDDING_API_DIMENSION".equals(name)
                ? "0"
                : COMPLETE_ENVIRONMENT.get(name);

        assertThatIllegalStateException()
                .isThrownBy(() -> PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                        new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), zeroDimension))
                .withMessageContaining("must be positive");
    }

    @Test
    void warnsWhenTheDeprecatedEmbeddingsPathVariableIsSetToAnUnexpectedValue() {
        Function<String, String> mismatchedPath = name -> "SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH".equals(name)
                ? "/v1/embeddings"
                : COMPLETE_ENVIRONMENT.get(name);
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

        PrecomputeApiEmbeddingsCli.resolveConfig(new String[0],
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), mismatchedPath);

        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("WARNING")
                .contains("SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH");
    }

    @Test
    void printsNoWarningWhenTheEmbeddingsPathVariableIsUnset() {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

        PrecomputeApiEmbeddingsCli.resolveConfig(new String[0], new PrintStream(outBuffer, true, StandardCharsets.UTF_8),
                COMPLETE_ENVIRONMENT::get);

        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
    }
}
