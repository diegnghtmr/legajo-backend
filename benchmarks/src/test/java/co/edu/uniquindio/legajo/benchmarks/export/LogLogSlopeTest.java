package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the least-squares log-log slope (the empirical-vs-theoretical complexity
 * comparison). Written before {@link LogLogSlope} exists (strict TDD).
 */
class LogLogSlopeTest {

    @Test
    void quadraticGrowthYieldsASlopeOfTwo() {
        List<SizeScore> points = List.of(
                new SizeScore(50, 50.0 * 50.0),
                new SizeScore(100, 100.0 * 100.0),
                new SizeScore(200, 200.0 * 200.0),
                new SizeScore(400, 400.0 * 400.0),
                new SizeScore(800, 800.0 * 800.0));

        double slope = LogLogSlope.of(points);

        assertThat(slope).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void linearGrowthYieldsASlopeOfOne() {
        List<SizeScore> points = List.of(
                new SizeScore(5, 5.0), new SizeScore(10, 10.0), new SizeScore(20, 20.0),
                new SizeScore(40, 40.0), new SizeScore(80, 80.0));

        double slope = LogLogSlope.of(points);

        assertThat(slope).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void cubicGrowthYieldsASlopeOfThree() {
        List<SizeScore> points = List.of(
                new SizeScore(5, Math.pow(5, 3)), new SizeScore(10, Math.pow(10, 3)),
                new SizeScore(20, Math.pow(20, 3)), new SizeScore(40, Math.pow(40, 3)),
                new SizeScore(80, Math.pow(80, 3)));

        double slope = LogLogSlope.of(points);

        assertThat(slope).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void constantScoreYieldsASlopeOfZero() {
        List<SizeScore> points = List.of(
                new SizeScore(1, 10.0), new SizeScore(2, 10.0), new SizeScore(4, 10.0), new SizeScore(8, 10.0));

        double slope = LogLogSlope.of(points);

        assertThat(slope).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void rejectsFewerThanTwoPoints() {
        assertThatThrownBy(() -> LogLogSlope.of(List.of(new SizeScore(1, 1.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveSize() {
        assertThatThrownBy(() -> LogLogSlope.of(List.of(new SizeScore(0, 1.0), new SizeScore(1, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonPositiveScore() {
        assertThatThrownBy(() -> LogLogSlope.of(List.of(new SizeScore(1, 0.0), new SizeScore(2, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonFiniteSize() {
        assertThatThrownBy(() -> LogLogSlope.of(List.of(new SizeScore(Double.NaN, 1.0), new SizeScore(2, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInfiniteSize() {
        assertThatThrownBy(() -> LogLogSlope
                .of(List.of(new SizeScore(Double.POSITIVE_INFINITY, 1.0), new SizeScore(2, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANonFiniteScore() {
        assertThatThrownBy(() -> LogLogSlope.of(List.of(new SizeScore(1, Double.NaN), new SizeScore(2, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnInfiniteScore() {
        assertThatThrownBy(() -> LogLogSlope
                .of(List.of(new SizeScore(1, Double.POSITIVE_INFINITY), new SizeScore(2, 2.0))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
