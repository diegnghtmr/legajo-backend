package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes {@code benchmarks/results/jmh-results.csv} (TRD NFR-QA-10, TAC-18): the reference
 * harness as {@code #}-prefixed header lines, then a {@code
 * benchmark,family,parameter,size,score,error,unit} row per {@link JmhResultRecord}, each
 * classified through {@link BenchmarkFamilies}.
 */
public final class JmhResultsCsvWriter {

    private static final String COLUMN_HEADER = "benchmark,family,parameter,size,score,error,unit";

    private JmhResultsCsvWriter() {
    }

    /** Writes {@code output}, overwriting any existing file at that path. */
    public static void write(Path output, HarnessInfo harness, List<JmhResultRecord> records) {
        List<String> lines = new ArrayList<>(harness.toHeaderLines());
        lines.add(COLUMN_HEADER);
        for (JmhResultRecord record : records) {
            lines.add(toCsvLine(record));
        }
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.write(output, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + output, e);
        }
    }

    private static String toCsvLine(JmhResultRecord record) {
        BenchmarkFamily family = BenchmarkFamilies.classify(record.benchmark(), record.params());
        return String.join(",",
                record.benchmark(),
                family.family(),
                family.sizeParameterKey(),
                formatNumber(family.size()),
                formatNumber(record.score()),
                formatNumber(record.error()),
                record.unit());
    }

    private static String formatNumber(double value) {
        // Double.toString is locale-independent and round-trips exactly (TRD "Precisión
        // numérica": shortest round-trip double formatting), and already renders NaN as
        // "NaN" without a separate branch.
        return Double.toString(value);
    }
}
