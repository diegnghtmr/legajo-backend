package co.edu.uniquindio.legajo.infrastructure.embedding;

import co.edu.uniquindio.legajo.infrastructure.io.AtomicFileWriter;
import co.edu.uniquindio.legajo.port.EmbeddingRepository;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Output adapter for {@link EmbeddingRepository}: reads and writes one embedding cache file
 * (e.g. {@code data/embeddings-minilm.json}) exactly in the TRD §9 schema — pretty-printed,
 * UTF-8, a stable field order, and a trailing newline, mirroring
 * {@code JsonCorpusRepository}'s conventions for {@code corpus.json}.
 *
 * <p><b>Renormalization on load (TRD §6.3, "Invariante de norma unitaria (fijado)").</b>
 * Every vector's {@code values} are parsed to {@code double} and unconditionally
 * L2-renormalized, regardless of how close to unit length they already are; a stored norm
 * that deviates from 1 by more than 1e-6 is reported to {@code provenanceWarningSink} — a
 * warning only, never a load failure — while {@code preNormL2} stays exactly the
 * precompute-time value recorded on disk, never recomputed here. A stored vector with a
 * non-finite component, or a zero/non-finite norm (e.g. a truncated or hand-edited cache
 * left it all-zero), fails closed with {@link IllegalStateException} instead of silently
 * renormalizing into a vector of NaN.
 *
 * <p><b>Fails closed on a corpus mismatch (TRD §6.1).</b> {@code load()} compares the
 * cache's {@code corpusSha256} against {@code expectedCorpusSha256} (the corpus this
 * repository was bound to at construction time) and throws {@link IllegalStateException},
 * naming the {@code precomputeEmbeddings} Gradle task, if they differ.
 */
public final class JsonEmbeddingRepository implements EmbeddingRepository {

    private static final Logger LOGGER = System.getLogger(JsonEmbeddingRepository.class.getName());
    private static final DefaultIndenter LF_INDENTER = new DefaultIndenter("  ", "\n");
    private static final double UNIT_NORM_DEVIATION_WARNING_THRESHOLD = 1e-6;

    private final Path embeddingsPath;
    private final String provider;
    private final String expectedCorpusSha256;
    private final Consumer<String> provenanceWarningSink;
    private final JsonMapper jsonMapper;

    public JsonEmbeddingRepository(Path embeddingsPath, String provider, String expectedCorpusSha256) {
        this(embeddingsPath, provider, expectedCorpusSha256,
                message -> LOGGER.log(Level.WARNING, message));
    }

    public JsonEmbeddingRepository(Path embeddingsPath, String provider, String expectedCorpusSha256,
            Consumer<String> provenanceWarningSink) {
        this.embeddingsPath = Objects.requireNonNull(embeddingsPath, "embeddingsPath");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.expectedCorpusSha256 = Objects.requireNonNull(expectedCorpusSha256, "expectedCorpusSha256");
        this.provenanceWarningSink = Objects.requireNonNull(provenanceWarningSink, "provenanceWarningSink");
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    public EmbeddingCache load() {
        EmbeddingCacheJson json = readJson();

        if (!expectedCorpusSha256.equals(json.corpusSha256())) {
            throw new IllegalStateException(
                    ("embedding cache at %s was precomputed for a different corpus "
                            + "(cache corpusSha256=%s, loaded corpus corpusSha256=%s). "
                            + "Re-run the offline precompute command: ./gradlew :bootstrap:precomputeEmbeddings")
                            .formatted(embeddingsPath, json.corpusSha256(), expectedCorpusSha256));
        }

        List<EmbeddingVector> vectors = new ArrayList<>(json.vectors().size());
        for (EmbeddingVectorJson vectorJson : json.vectors()) {
            vectors.add(renormalizeOnLoad(vectorJson, json.model()));
        }
        return new EmbeddingCache(json.version(), json.corpusVersion(), json.corpusSha256(), json.model(),
                json.dimension(), vectors);
    }

    @Override
    public void save(EmbeddingCache cache) {
        Objects.requireNonNull(cache, "cache");
        EmbeddingCacheJson json = toJson(cache);
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
                .withObjectIndenter(LF_INDENTER)
                .withArrayIndenter(LF_INDENTER);
        String content = jsonMapper.writer().with(prettyPrinter).writeValueAsString(json);
        AtomicFileWriter.writeUtf8(embeddingsPath, content + "\n");
    }

    private EmbeddingCacheJson readJson() {
        try (InputStream in = Files.newInputStream(embeddingsPath)) {
            return jsonMapper.readValue(in, EmbeddingCacheJson.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read embedding cache from " + embeddingsPath, e);
        }
    }

    /**
     * Parses {@code vectorJson.values()} to {@code double} and unconditionally divides every
     * component by their current L2 norm (hand-written; TRD §3.3 — normalization is not
     * delegable), regardless of how close to 1 that norm already is. {@code preNormL2} is
     * copied through unchanged: it is precompute-time provenance, not something this loading
     * step recomputes.
     */
    private EmbeddingVector renormalizeOnLoad(EmbeddingVectorJson vectorJson, String model) {
        List<Double> storedValues = vectorJson.values();
        for (double component : storedValues) {
            if (!Double.isFinite(component)) {
                throw failClosed(vectorJson.id(), "a non-finite component (%s)".formatted(component));
            }
        }

        double loadedNorm = EmbeddingVector.l2Norm(storedValues);
        if (loadedNorm == 0.0 || !Double.isFinite(loadedNorm)) {
            throw failClosed(vectorJson.id(), "a zero or non-finite norm (%s)".formatted(loadedNorm));
        }

        if (Math.abs(loadedNorm - 1.0) > UNIT_NORM_DEVIATION_WARNING_THRESHOLD) {
            provenanceWarningSink.accept(
                    ("embedding cache %s: vector for document '%s' has a stored norm of %.15f "
                            + "(preNormL2 provenance: %.15f), deviating from unit length by more than %.0e; "
                            + "renormalizing on load")
                            .formatted(embeddingsPath, vectorJson.id(), loadedNorm, vectorJson.preNormL2(),
                                    UNIT_NORM_DEVIATION_WARNING_THRESHOLD));
        }

        List<Double> renormalized = new ArrayList<>(storedValues.size());
        for (double component : storedValues) {
            renormalized.add(component / loadedNorm);
        }
        return new EmbeddingVector(vectorJson.id(), provider, model, vectorJson.preNormL2(), renormalized);
    }

    /**
     * Fails closed (TRD §6.1 posture) on a corrupted cache entry — a truncated, hand-edited,
     * or half-written cache file — naming the offending document id and the precompute
     * command to re-run, consistent with the {@code corpusSha256} mismatch path above.
     */
    private IllegalStateException failClosed(String documentId, String problem) {
        return new IllegalStateException(
                ("embedding cache at %s: vector for document '%s' has %s. The cache file may be truncated, "
                        + "hand-edited, or half-written. Re-run the offline precompute command: "
                        + "./gradlew :bootstrap:precomputeEmbeddings")
                        .formatted(embeddingsPath, documentId, problem));
    }

    private static EmbeddingCacheJson toJson(EmbeddingCache cache) {
        List<EmbeddingVectorJson> vectors = cache.vectors().stream()
                .map(vector -> new EmbeddingVectorJson(vector.documentId(), vector.preNormL2(), vector.values()))
                .toList();
        return new EmbeddingCacheJson(cache.version(), cache.corpusVersion(), cache.corpusSha256(), cache.model(),
                cache.dimension(), vectors);
    }
}
