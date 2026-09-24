package co.edu.uniquindio.legajo.benchmarks.export;

import java.util.Objects;
import java.util.Optional;

/**
 * One JMH result row's classification for the CSV/slopes export: the curve/family
 * it belongs to, which of its {@code @Param}s is the varying size, that size's numeric
 * value, and the documented theoretical complexity exponent for this family, when this
 * family is a curve at all (a fixed-n SLO measurement is not).
 */
public record BenchmarkFamily(String family, String sizeParameterKey, double size, Optional<Double> theoreticalExponent) {

    public BenchmarkFamily {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(sizeParameterKey, "sizeParameterKey");
        Objects.requireNonNull(theoreticalExponent, "theoreticalExponent");
    }
}
