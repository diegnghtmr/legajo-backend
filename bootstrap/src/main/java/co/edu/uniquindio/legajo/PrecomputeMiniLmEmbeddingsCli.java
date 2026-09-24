package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.JsonEmbeddingRepository;
import co.edu.uniquindio.legajo.infrastructure.embedding.MiniLmEmbedder;
import co.edu.uniquindio.legajo.similarity.EmbeddingCache;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entry point for {@code ./gradlew :bootstrap:precomputeEmbeddings [--args="..."]}:
 * runs {@code all-MiniLM-L6-v2} locally over every document's
 * abstract in {@code corpus.json} and writes {@code embeddings-minilm.json}, binding it to
 * the loaded corpus's frozen {@code corpusSha256} so a stale cache
 * fails closed at load time ({@link JsonEmbeddingRepository}).
 *
 * <p>A plain {@code main}, like {@link IngestCli} and {@link VerifyCorpusCli}: a one-shot
 * offline batch job needs no Spring context. The tokenizer and ONNX model files are
 * downloaded once into {@code build/models/minilm/} (never versioned — like the teacher PDFs,
 * these are large third-party binaries, not project data) and reused on later runs.
 *
 * <p><b>Run this only via the {@code precomputeEmbeddings} Gradle task</b>, not the plain
 * {@code bootstrap} module runtime classpath: empirically, loading both the HuggingFace
 * tokenizer's native library and ONNX Runtime's native library in the same JVM process
 * segfaults the process when Spring Boot's actuator/micrometer jars are also present on the
 * classpath (reproduced directly; root cause not identified beyond "micrometer's jars
 * present" — plausibly a native symbol or memory-layout interaction, since no individual
 * micrometer artifact reproduces it alone, only the full set together). The
 * {@code precomputeEmbeddings} task's classpath is scoped to avoid this.
 *
 * <p><b>Testable entry point.</b> Running the embedder needs
 * the real ONNX/tokenizer native libraries and either a network download or a
 * pre-populated {@code build/models/minilm/} cache, so {@code main}'s argument
 * resolution is isolated in {@link #resolveOptions(String[])} (pure parsing/defaulting,
 * no I/O), and the network-download helper {@link #downloadIfMissing(Path, String)} is
 * independently testable for its two fail-closed/idempotency properties: it never
 * touches the network when the target file already exists, and a syntactically invalid
 * URL fails before any network I/O is attempted.
 */
public final class PrecomputeMiniLmEmbeddingsCli {

    private static final String DEFAULT_TOKENIZER_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";
    private static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";

    private PrecomputeMiniLmEmbeddingsCli() {
    }

    public static void main(String[] args) {
        Options options = resolveOptions(args);

        downloadIfMissing(options.tokenizerPath(), options.tokenizerUrl());
        downloadIfMissing(options.modelPath(), options.modelUrl());

        Corpus corpus = new JsonCorpusRepository(Path.of(options.corpusPath())).load();
        System.out.printf("precompute-embeddings: %d document(s) loaded from %s (corpusSha256=%s)%n",
                corpus.documents().size(), options.corpusPath(), corpus.corpusSha256());

        List<EmbeddingVector> vectors = new ArrayList<>(corpus.documents().size());
        try (MiniLmEmbedder embedder = new MiniLmEmbedder(options.tokenizerPath(), options.modelPath())) {
            for (CorpusDocument document : corpus.documents()) {
                EmbeddingVector vector = embedder.embed(document.id(), document.abstractText());
                vectors.add(vector);
                System.out.printf("  %s: dimension=%d preNormL2=%.6f%n", document.id(), vector.dimension(),
                        vector.preNormL2());
            }
        }

        int dimension = vectors.isEmpty() ? 0 : vectors.getFirst().dimension();
        EmbeddingCache cache = new EmbeddingCache("1.0", corpus.version(), corpus.corpusSha256(),
                MiniLmEmbedder.MODEL, dimension, vectors);
        new JsonEmbeddingRepository(Path.of(options.outputPath()), MiniLmEmbedder.PROVIDER, corpus.corpusSha256())
                .save(cache);

        System.out.printf("precompute-embeddings: wrote %d vector(s) (dimension=%d) to %s%n", vectors.size(),
                dimension, options.outputPath());
    }

    /** The resolved {@code --corpus}/{@code --output}/{@code --tokenizer[-url]}/{@code --model[-url]} arguments. */
    record Options(String corpusPath, String outputPath, Path tokenizerPath, Path modelPath, String tokenizerUrl,
            String modelUrl) {
    }

    static Options resolveOptions(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        return new Options(
                options.getOrDefault("corpus", "data/corpus.json"),
                options.getOrDefault("output", "data/embeddings-minilm.json"),
                Path.of(options.getOrDefault("tokenizer", "build/models/minilm/tokenizer.json")),
                Path.of(options.getOrDefault("model", "build/models/minilm/model.onnx")),
                options.getOrDefault("tokenizer-url", DEFAULT_TOKENIZER_URL),
                options.getOrDefault("model-url", DEFAULT_MODEL_URL));
    }

    static void downloadIfMissing(Path path, String url) {
        if (Files.exists(path)) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            URL source = new URI(url).toURL();
            System.out.println("precompute-embeddings: downloading " + url + " to " + path);
            try (InputStream in = source.openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to download " + url + " to " + path, e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid download URL: " + url, e);
        }
    }
}
