package co.edu.uniquindio.legajo.infrastructure.embedding;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import co.edu.uniquindio.legajo.similarity.EmbeddingVector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs {@code all-MiniLM-L6-v2} locally over one abstract at a time for the offline
 * precompute job, on DJL 0.36.x with ONNX Runtime 1.29 CPU. Model inference itself — the
 * HuggingFace tokenizer and the ONNX Runtime forward pass — is fine to run through a
 * library; the windowing ({@link MiniLmWindowing}), the mean pooling
 * ({@link MiniLmPooling}), and the final L2 normalization ({@link
 * co.edu.uniquindio.legajo.similarity.EmbeddingVector#normalize}) are hand-written, exactly
 * the parts that must not be delegated to a library.
 *
 * <p><b>Pipeline (the fixed MiniLM token-limit rule).</b> The abstract's raw wordpiece
 * ids (no special tokens, since {@link MiniLmWindowing} windows the content alone) are split
 * into windows of at most {@link MiniLmWindowing#MAX_CONTENT_TOKENS_PER_WINDOW} tokens; each
 * window gets its own hand-added {@code [CLS]}/{@code [SEP]} pair (derived once from this
 * tokenizer, never hardcoded) and is run through the model separately; each window's token
 * embeddings are mean-pooled; the per-window vectors are mean-pooled again; and the result is
 * L2-normalized, with the pre-normalization norm recorded as {@code preNormL2} provenance.
 *
 * <p>Not unit-tested directly: it needs a real network-downloaded tokenizer and a ~90MB ONNX
 * model, so it is exercised by the real offline precompute run instead of
 * {@code ./gradlew test}. {@link MiniLmWindowing} and {@link MiniLmPooling} — the
 * hand-written logic that must not be delegated to a library — are unit-tested with
 * fabricated inputs.
 */
public final class MiniLmEmbedder implements AutoCloseable {

    /** The {@code provider} value every {@link EmbeddingVector} built by this class carries. */
    public static final String PROVIDER = "local";

    /** The {@code model} value every {@link EmbeddingVector} built by this class carries. */
    public static final String MODEL = "all-MiniLM-L6-v2";

    private final HuggingFaceTokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final long clsId;
    private final long sepId;

    public MiniLmEmbedder(Path tokenizerJsonPath, Path modelOnnxPath) {
        Objects.requireNonNull(tokenizerJsonPath, "tokenizerJsonPath");
        Objects.requireNonNull(modelOnnxPath, "modelOnnxPath");
        try {
            this.tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokenizerJsonPath)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load MiniLM tokenizer from " + tokenizerJsonPath, e);
        }
        this.environment = OrtEnvironment.getEnvironment();
        try {
            this.session = environment.createSession(modelOnnxPath.toString(), new OrtSession.SessionOptions());
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to load MiniLM ONNX model from " + modelOnnxPath, e);
        }

        // Derive [CLS]/[SEP] ids from this exact tokenizer rather than hardcoding the usual
        // BERT vocabulary constants (101/102): encoding any short text with special tokens
        // enabled always wraps it as [CLS] ... [SEP].
        long[] probeIds = tokenizer.encode("x", true, false).getIds();
        this.clsId = probeIds[0];
        this.sepId = probeIds[probeIds.length - 1];
    }

    /**
     * Embeds {@code rawAbstract} for {@code documentId}: windows it, runs MiniLM once per
     * window, mean-pools each window's token embeddings, mean-pools the window vectors, and
     * L2-normalizes the result.
     */
    public EmbeddingVector embed(String documentId, String rawAbstract) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(rawAbstract, "rawAbstract");

        long[] contentIds = tokenizer.encode(rawAbstract, false, false).getIds();
        List<long[]> windows = MiniLmWindowing.windowize(contentIds);

        List<List<Double>> windowVectors = new ArrayList<>(windows.size());
        for (long[] window : windows) {
            windowVectors.add(embedWindow(window));
        }

        List<Double> pooled = MiniLmPooling.meanPoolWindows(windowVectors);
        return EmbeddingVector.normalize(documentId, PROVIDER, MODEL, pooled);
    }

    private List<Double> embedWindow(long[] contentIds) {
        int length = contentIds.length + 2;
        long[] ids = new long[length];
        long[] attentionMask = new long[length];
        long[] tokenTypeIds = new long[length];

        ids[0] = clsId;
        System.arraycopy(contentIds, 0, ids, 1, contentIds.length);
        ids[length - 1] = sepId;
        Arrays.fill(attentionMask, 1L);
        // tokenTypeIds stays all zeros: a single-sequence input (this pipeline embeds one
        // abstract at a time, never a sentence pair).

        try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment, new long[][] { ids });
                OnnxTensor maskTensor = OnnxTensor.createTensor(environment, new long[][] { attentionMask });
                OnnxTensor typeTensor = OnnxTensor.createTensor(environment, new long[][] { tokenTypeIds })) {
            Map<String, OnnxTensor> inputs =
                    Map.of("input_ids", idsTensor, "attention_mask", maskTensor, "token_type_ids", typeTensor);
            try (OrtSession.Result result = session.run(inputs)) {
                float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
                return MiniLmPooling.meanPoolTokens(lastHiddenState[0], attentionMask);
            }
        } catch (OrtException e) {
            throw new IllegalStateException("MiniLM ONNX inference failed", e);
        }
    }

    @Override
    public void close() {
        tokenizer.close();
        try {
            session.close();
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to close the MiniLM ONNX session", e);
        }
    }
}
