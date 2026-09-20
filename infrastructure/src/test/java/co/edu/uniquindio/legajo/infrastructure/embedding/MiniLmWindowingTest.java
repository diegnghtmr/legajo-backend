package co.edu.uniquindio.legajo.infrastructure.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Hand-written windowing of raw (no special tokens) wordpiece ids into groups of at most
 * {@link MiniLmWindowing#MAX_CONTENT_TOKENS_PER_WINDOW} each (TRD §6.3, "Límite de tokens de
 * MiniLM": the model accepts 256 wordpiece tokens total, so each window reserves 2 slots for
 * the hand-added {@code [CLS]}/{@code [SEP]} tokens). No tokenizer or model is involved here —
 * these are plain {@code long[]} arrays standing in for whatever a real tokenizer would
 * produce.
 */
class MiniLmWindowingTest {

    @Test
    void contentThatFitsInOneWindowIsNotSplit() {
        long[] content = {1, 2, 3, 4, 5};

        List<long[]> windows = MiniLmWindowing.windowize(content);

        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void contentLongerThanTheWindowLimitIsSplitIntoMultipleWindows() {
        int limit = MiniLmWindowing.MAX_CONTENT_TOKENS_PER_WINDOW;
        long[] content = new long[limit + 10];
        for (int i = 0; i < content.length; i++) {
            content[i] = i;
        }

        List<long[]> windows = MiniLmWindowing.windowize(content);

        assertThat(windows).hasSize(2);
        assertThat(windows.get(0)).hasSize(limit);
        assertThat(windows.get(1)).hasSize(10);
    }

    @Test
    void windowsConcatenateBackToTheOriginalContentInOrder() {
        int limit = MiniLmWindowing.MAX_CONTENT_TOKENS_PER_WINDOW;
        long[] content = new long[2 * limit + 3];
        for (int i = 0; i < content.length; i++) {
            content[i] = i;
        }

        List<long[]> windows = MiniLmWindowing.windowize(content);

        long[] reconstructed = windows.stream()
                .reduce(new long[0], (acc, window) -> {
                    long[] merged = java.util.Arrays.copyOf(acc, acc.length + window.length);
                    System.arraycopy(window, 0, merged, acc.length, window.length);
                    return merged;
                });
        assertThat(reconstructed).containsExactly(content);
    }

    @Test
    void emptyContentStillProducesExactlyOneWindow() {
        List<long[]> windows = MiniLmWindowing.windowize(new long[0]);

        assertThat(windows).hasSize(1);
        assertThat(windows.getFirst()).isEmpty();
    }

    @Test
    void aWindowNeverExceedsTheContentLimit() {
        long[] content = new long[MiniLmWindowing.MAX_CONTENT_TOKENS_PER_WINDOW * 3];

        List<long[]> windows = MiniLmWindowing.windowize(content);

        assertThat(windows).allSatisfy(
                window -> assertThat(window.length).isLessThanOrEqualTo(MiniLmWindowing.MAX_CONTENT_TOKENS_PER_WINDOW));
    }

    @Test
    void rejectsANullContentArray() {
        assertThatNullPointerException().isThrownBy(() -> MiniLmWindowing.windowize(null));
    }
}
