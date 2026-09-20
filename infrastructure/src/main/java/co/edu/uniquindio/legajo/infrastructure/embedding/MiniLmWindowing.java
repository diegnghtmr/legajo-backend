package co.edu.uniquindio.legajo.infrastructure.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Hand-written windowing of a MiniLM abstract's raw (no special tokens) wordpiece ids into
 * groups of at most {@link #MAX_CONTENT_TOKENS_PER_WINDOW} each (TRD §6.3, "Límite de
 * tokens de MiniLM"): {@code all-MiniLM-L6-v2} accepts 256 wordpiece tokens per forward
 * pass, so every window reserves 2 of those slots for the {@code [CLS]}/{@code [SEP]} tokens
 * {@link MiniLmEmbedder} adds around each window's content.
 */
final class MiniLmWindowing {

    static final int MAX_MODEL_TOKENS = 256;
    static final int MAX_CONTENT_TOKENS_PER_WINDOW = MAX_MODEL_TOKENS - 2;

    private MiniLmWindowing() {
    }

    /**
     * Splits {@code contentIds} into consecutive windows of at most
     * {@link #MAX_CONTENT_TOKENS_PER_WINDOW} ids each, preserving order. Always returns at
     * least one window — an empty {@code contentIds} yields one empty window — so callers
     * never need to special-case "no windows".
     */
    static List<long[]> windowize(long[] contentIds) {
        Objects.requireNonNull(contentIds, "contentIds");
        if (contentIds.length == 0) {
            List<long[]> singleEmptyWindow = new ArrayList<>(1);
            singleEmptyWindow.add(new long[0]);
            return singleEmptyWindow;
        }

        List<long[]> windows = new ArrayList<>();
        int start = 0;
        while (start < contentIds.length) {
            int end = Math.min(start + MAX_CONTENT_TOKENS_PER_WINDOW, contentIds.length);
            windows.add(Arrays.copyOfRange(contentIds, start, end));
            start = end;
        }
        return windows;
    }
}
