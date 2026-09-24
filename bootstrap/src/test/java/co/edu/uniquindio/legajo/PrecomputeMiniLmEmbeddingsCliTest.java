package co.edu.uniquindio.legajo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@code precomputeEmbeddings}'s argument contract and the fail-closed/idempotency
 * contract of its download helper. Running the embedder needs
 * real ONNX/tokenizer native libraries, so this stays scoped to what does not: {@link
 * PrecomputeMiniLmEmbeddingsCli#resolveOptions(String[])} (pure parsing/defaulting) and
 * {@link PrecomputeMiniLmEmbeddingsCli#downloadIfMissing(Path, String)}, exercised only
 * in the cases that need no network I/O.
 */
class PrecomputeMiniLmEmbeddingsCliTest {

    @Test
    void resolveOptionsAppliesDefaultsWhenNoArgumentsAreGiven() {
        PrecomputeMiniLmEmbeddingsCli.Options options = PrecomputeMiniLmEmbeddingsCli.resolveOptions(new String[0]);

        assertThat(options.corpusPath()).isEqualTo("data/corpus.json");
        assertThat(options.outputPath()).isEqualTo("data/embeddings-minilm.json");
        assertThat(options.tokenizerPath()).isEqualTo(Path.of("build/models/minilm/tokenizer.json"));
        assertThat(options.modelPath()).isEqualTo(Path.of("build/models/minilm/model.onnx"));
        assertThat(options.tokenizerUrl()).contains("all-MiniLM-L6-v2").contains("tokenizer.json");
        assertThat(options.modelUrl()).contains("all-MiniLM-L6-v2").contains("model.onnx");
    }

    @Test
    void resolveOptionsHonorsEveryExplicitOverride() {
        PrecomputeMiniLmEmbeddingsCli.Options options = PrecomputeMiniLmEmbeddingsCli.resolveOptions(new String[] {
                "--corpus=custom/corpus.json", "--output=custom/embeddings.json",
                "--tokenizer=custom/tokenizer.json", "--model=custom/model.onnx",
                "--tokenizer-url=https://example.invalid/tokenizer.json",
                "--model-url=https://example.invalid/model.onnx"});

        assertThat(options.corpusPath()).isEqualTo("custom/corpus.json");
        assertThat(options.outputPath()).isEqualTo("custom/embeddings.json");
        assertThat(options.tokenizerPath()).isEqualTo(Path.of("custom/tokenizer.json"));
        assertThat(options.modelPath()).isEqualTo(Path.of("custom/model.onnx"));
        assertThat(options.tokenizerUrl()).isEqualTo("https://example.invalid/tokenizer.json");
        assertThat(options.modelUrl()).isEqualTo("https://example.invalid/model.onnx");
    }

    /**
     * The idempotency half of the fail-closed contract: a target that already exists
     * must never be re-fetched. Pointing at an unreachable URL proves no network
     * attempt was made — a real attempt would fail with an UncheckedIOException instead
     * of returning normally.
     */
    @Test
    void downloadIfMissingSkipsTheDownloadWhenTheTargetAlreadyExists(@TempDir Path tempDir) throws IOException {
        Path existing = tempDir.resolve("tokenizer.json");
        Files.writeString(existing, "already-cached");

        PrecomputeMiniLmEmbeddingsCli.downloadIfMissing(existing, "http://unreachable.invalid/should-not-be-fetched");

        assertThat(Files.readString(existing)).isEqualTo("already-cached");
    }

    /**
     * The other half: a syntactically invalid URL must fail before any network I/O is
     * even attempted, with a message naming the offending URL rather than an unrelated
     * network-timeout stack trace.
     */
    @Test
    void downloadIfMissingFailsClosedForAMalformedUrlWithoutAttemptingNetworkIo(@TempDir Path tempDir) {
        Path target = tempDir.resolve("model.onnx");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> PrecomputeMiniLmEmbeddingsCli.downloadIfMissing(target, "not a valid url"))
                .withMessageContaining("not a valid url");
        assertThat(Files.exists(target)).isFalse();
    }
}
