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
 * benchmark,family,parameter,size,score,error,unit} row per {@link ClassifiedBenchmarkResult}.
 * Every result this writer receives is already classified ({@link
 * BenchmarkFamilies#classifyAll}, called once by {@link JmhExportCli} before either CSV writer
 * runs): a record that could not be classified never reaches this writer at all, it aborts the
 * whole export before any file is written, so this writer has nothing left to skip or degrade.
 */
public final class JmhResultsCsvWriter {

    private static final String COLUMN_HEADER = "benchmark,family,parameter,size,score,error,unit";

    private JmhResultsCsvWriter() {
    }

    /** Writes {@code output}, overwriting any existing file at that path. */
    public static void write(Path output, HarnessInfo harness, List<ClassifiedBenchmarkResult> results) {
        List<String> lines = new ArrayList<>(harness.toHeaderLines());
        lines.add(COLUMN_HEADER);
        for (ClassifiedBenchmarkResult result : results) {
            lines.add(toCsvLine(result));
        }
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.write(output, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + output, e);
        }
    }

    private static String toCsvLine(ClassifiedBenchmarkResult result) {
        JmhResultRecord record = result.record();
        BenchmarkFamily family = result.family();
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
