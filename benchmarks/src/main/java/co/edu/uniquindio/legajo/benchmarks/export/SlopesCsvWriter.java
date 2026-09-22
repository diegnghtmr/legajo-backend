package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Writes {@code benchmarks/results/slopes.csv} (TRD §6.3/§6.4/§6.5, TAC-18): one row per
 * curve family with a documented theoretical exponent ({@link BenchmarkFamilies}) and at
 * least two distinct sizes, giving the least-squares log-log slope of its empirical scores
 * ({@link LogLogSlope}) next to that theoretical exponent. A fixed-n SLO family (no
 * theoretical exponent) or a family with fewer than two data points is not a curve and is
 * silently excluded, never reported with a nonsensical or undefined slope.
 */
public final class SlopesCsvWriter {

    private static final String COLUMN_HEADER = "family,points,empiricalSlope,theoreticalExponent";

    private SlopesCsvWriter() {
    }

    /** Writes {@code output}, overwriting any existing file at that path. */
    public static void write(Path output, List<JmhResultRecord> records) {
        Map<String, List<SizeScore>> pointsByFamily = new LinkedHashMap<>();
        Map<String, Optional<Double>> exponentByFamily = new LinkedHashMap<>();

        for (JmhResultRecord record : records) {
            BenchmarkFamily family = BenchmarkFamilies.classify(record.benchmark(), record.params());
            pointsByFamily.computeIfAbsent(family.family(), key -> new ArrayList<>())
                    .add(new SizeScore(family.size(), record.score()));
            exponentByFamily.put(family.family(), family.theoreticalExponent());
        }

        List<String> lines = new ArrayList<>();
        lines.add(COLUMN_HEADER);
        pointsByFamily.keySet().stream().sorted().forEach(family -> {
            Optional<Double> theoreticalExponent = exponentByFamily.get(family);
            List<SizeScore> points = pointsByFamily.get(family);
            if (theoreticalExponent.isEmpty() || points.size() < 2) {
                return;
            }
            double slope = LogLogSlope.of(points);
            lines.add(String.join(",",
                    family,
                    String.valueOf(points.size()),
                    formatFixed(slope),
                    formatFixed(theoreticalExponent.get())));
        });

        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.write(output, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + output, e);
        }
    }

    private static String formatFixed(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
