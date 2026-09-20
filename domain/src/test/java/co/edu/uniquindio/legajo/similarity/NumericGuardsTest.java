package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * {@link NumericGuards} centralizes the finiteness rule every similarity record's compact
 * constructor needs: {@code Math.abs(actual - expected) > tolerance} is silently {@code false}
 * when either operand is {@code NaN} (a NaN comparison is never {@code >}, {@code <}, or
 * {@code ==} anything, including itself), so a tolerance or range comparison must reject
 * non-finite input explicitly instead of relying on the bare comparison alone.
 */
class NumericGuardsTest {

    private static final double TOLERANCE = 1e-9;

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void requireFiniteRejectsNonFiniteValues(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NumericGuards.requireFinite(nonFinite, "value"))
                .withMessageContaining("value")
                .withMessageContaining("finite");
    }

    @Test
    void requireFiniteAcceptsAFiniteValue() {
        assertThatNoException().isThrownBy(() -> NumericGuards.requireFinite(1.0, "value"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void requireNonNegativeFiniteRejectsNonFiniteValues(double nonFinite) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NumericGuards.requireNonNegativeFinite(nonFinite, "value"))
                .withMessageContaining("finite");
    }

    @Test
    void requireNonNegativeFiniteRejectsANegativeValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NumericGuards.requireNonNegativeFinite(-1.0, "value"))
                .withMessageContaining("non-negative");
    }

    @Test
    void requireNonNegativeFiniteAcceptsZeroAndPositiveValues() {
        assertThatNoException().isThrownBy(() -> NumericGuards.requireNonNegativeFinite(0.0, "value"));
        assertThatNoException().isThrownBy(() -> NumericGuards.requireNonNegativeFinite(5.0, "value"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void isOutOfToleranceIsTrueWhenTheActualValueIsNonFiniteEvenAgainstAnIdenticalExpected(double nonFinite) {
        // The exact bug this guards against: Math.abs(NaN - NaN) is NaN, and NaN > tolerance
        // is false, so a bare tolerance comparison would silently accept this pair.
        assertThat(NumericGuards.isOutOfTolerance(nonFinite, nonFinite, TOLERANCE)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void isOutOfToleranceIsTrueWhenTheExpectedValueIsNonFinite(double nonFinite) {
        assertThat(NumericGuards.isOutOfTolerance(1.0, nonFinite, TOLERANCE)).isTrue();
    }

    @Test
    void isOutOfToleranceIsFalseWithinTolerance() {
        assertThat(NumericGuards.isOutOfTolerance(1.0000000001, 1.0, TOLERANCE)).isFalse();
    }

    @Test
    void isOutOfToleranceIsTrueBeyondTolerance() {
        assertThat(NumericGuards.isOutOfTolerance(1.1, 1.0, TOLERANCE)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void isOutOfRangeIsTrueForNonFiniteValues(double nonFinite) {
        assertThat(NumericGuards.isOutOfRange(nonFinite, 0.0, 1.0, TOLERANCE)).isTrue();
    }

    @Test
    void isOutOfRangeIsFalseWithinBounds() {
        assertThat(NumericGuards.isOutOfRange(0.5, 0.0, 1.0, TOLERANCE)).isFalse();
    }

    @Test
    void isOutOfRangeToleratesOvershootAtTheBoundaries() {
        assertThat(NumericGuards.isOutOfRange(-1e-10, 0.0, 1.0, TOLERANCE)).isFalse();
        assertThat(NumericGuards.isOutOfRange(1.0 + 1e-10, 0.0, 1.0, TOLERANCE)).isFalse();
    }

    @Test
    void isOutOfRangeIsTrueBeyondBounds() {
        assertThat(NumericGuards.isOutOfRange(-0.01, 0.0, 1.0, TOLERANCE)).isTrue();
        assertThat(NumericGuards.isOutOfRange(1.01, 0.0, 1.0, TOLERANCE)).isTrue();
    }
}
