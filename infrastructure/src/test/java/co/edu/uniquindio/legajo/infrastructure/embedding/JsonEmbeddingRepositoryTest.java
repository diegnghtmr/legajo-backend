package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link JsonEmbeddingRepository} against TRD §9's embeddings-cache schema: {@code version},
 * {@code corpusVersion}, {@code corpusSha256}, {@code model}, {@code dimension},
 * {@code vectors[{id, preNormL2, values}]}. Covers the load-time invariants of TRD §6.3,
 * "Invariante de norma unitaria (fijado)" — unconditional renormalization plus a provenance
 * warning on deviation — and the "fails closed" corpusSha256 binding of TRD §6.1.
 */
class JsonEmbeddingRepositoryTest {

    private static final String EXPECTED_CORPUS_SHA_256 = "corpus-hash-abc";

    @Test
    void savesAndLoadsAnEquivalentCache(@TempDir Path tempDir) {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);
        EmbeddingCache original = twoVectorCache();

        repository.save(original);
        EmbeddingCache loaded = repository.load();

        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void jsonRoundTripPreservesBitIdenticalDoubleValues(@TempDir Path tempDir) {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);
        EmbeddingVector original = new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 5.814322,
                List.of(0.6, 0.8, 0.0));
        repository.save(new EmbeddingCache("1.0", "1.0", EXPECTED_CORPUS_SHA_256, "all-MiniLM-L6-v2", 3,
                List.of(original)));

        EmbeddingVector loaded = repository.load().find("d01").orElseThrow();

        for (int i = 0; i < original.values().size(); i++) {
            assertThat(Double.doubleToLongBits(loaded.values().get(i)))
                    .isEqualTo(Double.doubleToLongBits(original.values().get(i)));
        }
        assertThat(Double.doubleToLongBits(loaded.preNormL2())).isEqualTo(Double.doubleToLongBits(original.preNormL2()));
    }

    @Test
    void writesUtf8PrettyPrintedJsonWithTheTrdFieldOrder(@TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);

        repository.save(twoVectorCache());
        String content = Files.readString(path, StandardCharsets.UTF_8);

        assertThat(content).endsWith("\n").doesNotEndWith("\n\n");
        assertThat(content.indexOf("\"version\"")).isLessThan(content.indexOf("\"corpusVersion\""));
        assertThat(content.indexOf("\"corpusVersion\"")).isLessThan(content.indexOf("\"corpusSha256\""));
        assertThat(content.indexOf("\"corpusSha256\"")).isLessThan(content.indexOf("\"model\""));
        assertThat(content.indexOf("\"model\"")).isLessThan(content.indexOf("\"dimension\""));
        assertThat(content.indexOf("\"dimension\"")).isLessThan(content.indexOf("\"vectors\""));
        assertThat(content.indexOf("\"id\"")).isLessThan(content.indexOf("\"preNormL2\""));
        assertThat(content.indexOf("\"preNormL2\"")).isLessThan(content.indexOf("\"values\""));
    }

    @Test
    void loadingAMissingFileFailsWithAnUncheckedIoException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(missing, "local", EXPECTED_CORPUS_SHA_256);

        assertThatThrownBy(repository::load)
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void loadingACacheForADifferentCorpusFailsClosedAndNamesThePrecomputeCommand(@TempDir Path tempDir) {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository writer = new JsonEmbeddingRepository(path, "local", "some-other-corpus-hash");
        writer.save(new EmbeddingCache("1.0", "1.0", "some-other-corpus-hash", "all-MiniLM-L6-v2", 2,
                List.of(new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 1.0, List.of(0.6, 0.8)))));

        JsonEmbeddingRepository reader = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);

        assertThatIllegalStateException()
                .isThrownBy(reader::load)
                .withMessageContaining("some-other-corpus-hash")
                .withMessageContaining(EXPECTED_CORPUS_SHA_256)
                .withMessageContaining("precomputeEmbeddings");
    }

    @Test
    void loadingAnAllZeroVectorFailsClosedAndNamesThePrecomputeCommand(@TempDir Path tempDir) throws IOException {
        // A truncated or half-written cache file can leave a vector's values all zero;
        // dividing by a zero norm would otherwise silently yield NaN per component.
        Path path = tempDir.resolve("embeddings-minilm.json");
        Files.writeString(path, cacheJsonWithValues("d01", "0.0, 0.0"), StandardCharsets.UTF_8);
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);

        assertThatIllegalStateException()
                .isThrownBy(repository::load)
                .withMessageContaining("d01")
                .withMessageContaining("precomputeEmbeddings");
    }

    @Test
    void loadingANonFiniteComponentFailsClosedAndNamesThePrecomputeCommand(@TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("embeddings-minilm.json");
        Files.writeString(path, cacheJsonWithValues("d02", "\"NaN\", 0.8"), StandardCharsets.UTF_8);
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);

        assertThatIllegalStateException()
                .isThrownBy(repository::load)
                .withMessageContaining("d02")
                .withMessageContaining("precomputeEmbeddings");
    }

    @Test
    void loadingAnInfiniteComponentFailsClosedAndNamesThePrecomputeCommand(@TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("embeddings-minilm.json");
        Files.writeString(path, cacheJsonWithValues("d03", "\"Infinity\", 0.8"), StandardCharsets.UTF_8);
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);

        assertThatIllegalStateException()
                .isThrownBy(repository::load)
                .withMessageContaining("d03")
                .withMessageContaining("precomputeEmbeddings");
    }

    private static String cacheJsonWithValues(String documentId, String values) {
        return """
                {
                  "version" : "1.0",
                  "corpusVersion" : "1.0",
                  "corpusSha256" : "%s",
                  "model" : "all-MiniLM-L6-v2",
                  "dimension" : 2,
                  "vectors" : [ {
                    "id" : "%s",
                    "preNormL2" : 5.814322,
                    "values" : [ %s ]
                  } ]
                }
                """.formatted(EXPECTED_CORPUS_SHA_256, documentId, values);
    }

    @Test
    void loadingRenormalizesEveryVectorUnconditionallyAndWarnsOnDeviation(@TempDir Path tempDir) throws IOException {
        // A stored vector deviating from unit length by more than 1e-6 (TRD §6.3): raw
        // (0.6, 0.8) scaled by 1.000002, norm = 1.000002, |norm - 1| = 2e-6 > 1e-6.
        Path path = tempDir.resolve("embeddings-minilm.json");
        String json = """
                {
                  "version" : "1.0",
                  "corpusVersion" : "1.0",
                  "corpusSha256" : "%s",
                  "model" : "all-MiniLM-L6-v2",
                  "dimension" : 2,
                  "vectors" : [ {
                    "id" : "d01",
                    "preNormL2" : 5.814322,
                    "values" : [ 0.6000012, 0.8000016000000001 ]
                  } ]
                }
                """.formatted(EXPECTED_CORPUS_SHA_256);
        Files.writeString(path, json, StandardCharsets.UTF_8);

        List<String> warnings = new ArrayList<>();
        JsonEmbeddingRepository repository =
                new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256, warnings::add);

        EmbeddingCache cache = repository.load();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains("d01");
        EmbeddingVector loaded = cache.find("d01").orElseThrow();
        assertThat(EmbeddingVector.l2Norm(loaded.values())).isCloseTo(1.0, within(1e-9));
        // preNormL2 stays the stored provenance value, never recomputed on load.
        assertThat(loaded.preNormL2()).isCloseTo(5.814322, within(1e-9));
    }

    @Test
    void leavesNoTemporaryFileBehindAfterASuccessfulSave(@TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("embeddings-minilm.json");
        new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256).save(twoVectorCache());

        try (var entries = Files.list(tempDir)) {
            assertThat(entries.toList()).containsExactly(path);
        }
    }

    /**
     * Mirrors {@code JsonCorpusRepositoryTest}'s destructive-write proof: a directory
     * made read-only (but whose existing file remains file-level writable) still let
     * the old {@code Files.writeString(..., TRUNCATE_EXISTING)} implementation
     * overwrite the cache in place, because truncating a file's content needs only
     * file-level write permission. The fixed write-to-temp-then-move implementation
     * cannot create the temporary file under a read-only directory, so it fails before
     * touching the target, and the previously committed cache survives untouched.
     */
    @Test
    void preservesThePreviousFileWhenTheReplacementCannotBeWritten(@TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository repository = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);
        repository.save(twoVectorCache());
        String originalContent = Files.readString(path, StandardCharsets.UTF_8);

        assertThat(tempDir.toFile().setWritable(false)).isTrue();
        try {
            assertThatThrownBy(() -> repository.save(twoVectorCache()))
                    .isInstanceOf(UncheckedIOException.class);
        } finally {
            assertThat(tempDir.toFile().setWritable(true)).isTrue();
        }

        assertThat(Files.readString(path, StandardCharsets.UTF_8)).isEqualTo(originalContent);
    }

    @Test
    void loadingAnAlreadyUnitVectorLogsNoProvenanceWarning(@TempDir Path tempDir) {
        Path path = tempDir.resolve("embeddings-minilm.json");
        JsonEmbeddingRepository writer = new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256);
        writer.save(twoVectorCache());

        List<String> warnings = new ArrayList<>();
        JsonEmbeddingRepository reader =
                new JsonEmbeddingRepository(path, "local", EXPECTED_CORPUS_SHA_256, warnings::add);
        reader.load();

        assertThat(warnings).isEmpty();
    }

    private static EmbeddingCache twoVectorCache() {
        EmbeddingVector d1 = new EmbeddingVector("d01", "local", "all-MiniLM-L6-v2", 5.814322, List.of(0.6, 0.8));
        EmbeddingVector d2 = new EmbeddingVector("d02", "local", "all-MiniLM-L6-v2", 3.2, List.of(0.0, 1.0));
        return new EmbeddingCache("1.0", "1.0", EXPECTED_CORPUS_SHA_256, "all-MiniLM-L6-v2", 2, List.of(d1, d2));
    }
}
