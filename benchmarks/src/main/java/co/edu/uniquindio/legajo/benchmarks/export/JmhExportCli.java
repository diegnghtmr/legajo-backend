package co.edu.uniquindio.legajo.benchmarks.export;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the {@code :benchmarks:jmhExport} Gradle task: reads one JMH JSON results
 * file and writes {@code benchmarks/results/jmh-results.csv} and
 * {@code benchmarks/results/slopes.csv} (TRD NFR-QA-10, TAC-18; odd/tasks/jmh-benchmarks.md,
 * task J2). Arguments: {@code --input=<path>}, {@code --resultsCsv=<path>},
 * {@code --slopesCsv=<path>}.
 */
public final class JmhExportCli {

    private JmhExportCli() {
    }

    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        Path input = Path.of(require(parsed, "input"));
        Path resultsCsv = Path.of(require(parsed, "resultsCsv"));
        Path slopesCsv = Path.of(require(parsed, "slopesCsv"));
        run(input, resultsCsv, slopesCsv);
    }

    /** Reads {@code input} and writes both CSVs; separated from {@link #main} so it is directly testable. */
    static void run(Path input, Path resultsCsv, Path slopesCsv) {
        List<JmhResultRecord> records = JmhJsonResultsReader.read(input);
        HarnessInfo harness = HarnessInfo.collect();
        JmhResultsCsvWriter.write(resultsCsv, harness, records);
        SlopesCsvWriter.write(slopesCsv, records);
    }

    /** Parses {@code --key=value} arguments into a map, in encounter order. */
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("expected --key=value, got: " + arg);
            }
            String[] parts = arg.substring(2).split("=", 2);
            parsed.put(parts[0], parts[1]);
        }
        return parsed;
    }

    private static String require(Map<String, String> parsed, String key) {
        String value = parsed.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required --" + key + "=<value>");
        }
        return value;
    }
}
