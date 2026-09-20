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
 * Entry point for {@code ./gradlew :bootstrap:precomputeEmbeddings [--args="..."]}
 * (TRD §6.1, §6.3, §9): runs {@code all-MiniLM-L6-v2} locally over every document's
 * abstract in {@code corpus.json} and writes {@code embeddings-minilm.json}, binding it to
 * the loaded corpus's {@code corpusSha256} (TRD §6.1's "frozen" definition) so a stale cache
 * fails closed at load time ({@link JsonEmbeddingRepository}).
 *
 * <p>A plain {@code main}, like {@link IngestCli} and {@link VerifyCorpusCli}: a one-shot
 * offline batch job needs no Spring context. The tokenizer and ONNX model files are
 * downloaded once into {@code build/models/minilm/} (never versioned — like the teacher PDFs,
 * these are large third-party binaries, not project data) and reused on later runs.
 */
public final class PrecomputeMiniLmEmbeddingsCli {

    private static final String DEFAULT_TOKENIZER_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";
    private static final String DEFAULT_MODEL_URL =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";

    private PrecomputeMiniLmEmbeddingsCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String corpusPath = options.getOrDefault("corpus", "data/corpus.json");
        String outputPath = options.getOrDefault("output", "data/embeddings-minilm.json");
        Path tokenizerPath = Path.of(options.getOrDefault("tokenizer", "build/models/minilm/tokenizer.json"));
        Path modelPath = Path.of(options.getOrDefault("model", "build/models/minilm/model.onnx"));
        String tokenizerUrl = options.getOrDefault("tokenizer-url", DEFAULT_TOKENIZER_URL);
        String modelUrl = options.getOrDefault("model-url", DEFAULT_MODEL_URL);

        downloadIfMissing(tokenizerPath, tokenizerUrl);
        downloadIfMissing(modelPath, modelUrl);

        Corpus corpus = new JsonCorpusRepository(Path.of(corpusPath)).load();
        System.out.printf("precompute-embeddings: %d document(s) loaded from %s (corpusSha256=%s)%n",
                corpus.documents().size(), corpusPath, corpus.corpusSha256());

        List<EmbeddingVector> vectors = new ArrayList<>(corpus.documents().size());
        try (MiniLmEmbedder embedder = new MiniLmEmbedder(tokenizerPath, modelPath)) {
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
        new JsonEmbeddingRepository(Path.of(outputPath), MiniLmEmbedder.PROVIDER, corpus.corpusSha256()).save(cache);

        System.out.printf("precompute-embeddings: wrote %d vector(s) (dimension=%d) to %s%n", vectors.size(),
                dimension, outputPath);
    }

    private static void downloadIfMissing(Path path, String url) {
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
