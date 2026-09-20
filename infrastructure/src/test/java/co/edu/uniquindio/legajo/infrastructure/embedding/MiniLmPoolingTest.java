package co.edu.uniquindio.legajo.infrastructure.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * Hand-written mean pooling (TRD §3.3: pooling is not delegable under R-02), exercised with
 * fabricated token embeddings and window vectors standing in for whatever a real MiniLM ONNX
 * session would produce — no tokenizer or model needed to test this pure arithmetic.
 *
 * <pre>
 * meanPoolTokens: tokenEmbeddings = [[1,2],[3,4],[100,100]], mask=[1,1,0]
 *   -&gt; average of only the attended rows: ([1,2]+[3,4])/2 = [2,3]
 * meanPoolWindows: [[1,2],[3,4]] -&gt; [2,3]; [[0,0],[2,2],[4,4]] -&gt; [2,2]
 * </pre>
 */
class MiniLmPoolingTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void meanPoolTokensAveragesOnlyTheAttendedRows() {
        float[][] tokenEmbeddings = {{1f, 2f}, {3f, 4f}, {100f, 100f}};
        long[] attentionMask = {1, 1, 0};

        List<Double> pooled = MiniLmPooling.meanPoolTokens(tokenEmbeddings, attentionMask);

        assertThat(pooled.get(0)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(pooled.get(1)).isCloseTo(3.0, within(TOLERANCE));
    }

    @Test
    void meanPoolTokensWithAllTokensAttendedAveragesEveryRow() {
        float[][] tokenEmbeddings = {{2f, 0f}, {4f, 0f}};
        long[] attentionMask = {1, 1};

        List<Double> pooled = MiniLmPooling.meanPoolTokens(tokenEmbeddings, attentionMask);

        assertThat(pooled.get(0)).isCloseTo(3.0, within(TOLERANCE));
        assertThat(pooled.get(1)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void meanPoolTokensRejectsAMaskWithNoAttendedTokens() {
        float[][] tokenEmbeddings = {{1f, 2f}};
        long[] attentionMask = {0};

        assertThatIllegalArgumentException().isThrownBy(() -> MiniLmPooling.meanPoolTokens(tokenEmbeddings, attentionMask));
    }

    @Test
    void meanPoolTokensRejectsAMaskLengthMismatch() {
        float[][] tokenEmbeddings = {{1f, 2f}, {3f, 4f}};
        long[] attentionMask = {1};

        assertThatIllegalArgumentException().isThrownBy(() -> MiniLmPooling.meanPoolTokens(tokenEmbeddings, attentionMask));
    }

    @Test
    void meanPoolWindowsAveragesTwoWindowVectors() {
        List<List<Double>> windowVectors = List.of(List.of(1.0, 2.0), List.of(3.0, 4.0));

        List<Double> pooled = MiniLmPooling.meanPoolWindows(windowVectors);

        assertThat(pooled.get(0)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(pooled.get(1)).isCloseTo(3.0, within(TOLERANCE));
    }

    @Test
    void meanPoolWindowsAveragesThreeWindowVectors() {
        List<List<Double>> windowVectors = List.of(List.of(0.0, 0.0), List.of(2.0, 2.0), List.of(4.0, 4.0));

        List<Double> pooled = MiniLmPooling.meanPoolWindows(windowVectors);

        assertThat(pooled.get(0)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(pooled.get(1)).isCloseTo(2.0, within(TOLERANCE));
    }

    @Test
    void meanPoolWindowsWithASingleWindowReturnsItUnchanged() {
        List<List<Double>> windowVectors = List.of(List.of(7.0, 9.0));

        List<Double> pooled = MiniLmPooling.meanPoolWindows(windowVectors);

        assertThat(pooled).containsExactly(7.0, 9.0);
    }

    @Test
    void meanPoolWindowsRejectsAnEmptyList() {
        assertThatIllegalArgumentException().isThrownBy(() -> MiniLmPooling.meanPoolWindows(List.of()));
    }
}
