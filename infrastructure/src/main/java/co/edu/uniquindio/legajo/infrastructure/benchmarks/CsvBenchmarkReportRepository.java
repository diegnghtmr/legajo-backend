package co.edu.uniquindio.legajo.infrastructure.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkHarness;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkResult;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkSlope;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Infrastructure adapter for {@link BenchmarkReportRepository}: reads the two versioned JMH
 * export CSVs (TRD §6.6, {@code GET /benchmarks}; TRD 1.3.10) — {@code jmh-results.csv}'s
 * {@code #}-prefixed harness header and {@code
 * benchmark,family,parameter,size,score,error,unit} rows, and {@code slopes.csv}'s {@code
 * family,points,empiricalSlope,theoreticalExponent} rows — and shapes them into a {@link
 * BenchmarkReport}, exactly as exported. Never runs JMH, never recomputes a score or a slope.
 *
 * <p>Deliberately independent of the {@code :benchmarks} Gradle module: {@code
 * ArchitectureTest}'s {@code Benchmarks} layer "may not be accessed by any layer", because
 * the JMH harness is measurement tooling, never a runtime dependency of the deployed
 * application. This class hand-writes its own small, unforgiving CSV parser instead of
 * reusing {@code benchmarks.export.JmhResultsCsvWriter}; it only mirrors that writer's exact
 * column names and order by convention (both are read from the one CSV format TRD §6.6 and
 * the feature doc for {@code jmh-benchmarks} fix).
 *
 * <p>No CSV quoting is implemented: every field either writer emits is a Java-identifier-like
 * token, a kebab-case family name, a {@code Double.toString}/{@code "%.6f"} number, or a JMH
 * unit string ({@code ns/op}, {@code us/op}, {@code ms/op}) — none can contain a comma, so a
 * plain {@code split(",", -1)} is exact, not an approximation.
 *
 * <p>Every failure mode (missing file, a header line missing one of the six harness keys, a
 * data row with the wrong column count, or a value that does not parse as the expected
 * number) throws {@link IllegalStateException} naming the offending file and the export
 * command to re-run ({@value #EXPORT_COMMAND}) — the same fail-closed contract {@code
 * EmbeddingCacheStartupValidator} already applies to a missing/malformed embedding cache
 * (TRD §9): the versioned results files are deployment data, and an incomplete or corrupt one
 * must stop the server from starting, never serve a partial or wrong report.
 */
public final class CsvBenchmarkReportRepository implements BenchmarkReportRepository {

    static final String EXPORT_COMMAND = "./gradlew :benchmarks:jmh :benchmarks:jmhExport";

    private static final List<String> HARNESS_KEYS =
            List.of("cpuModel", "logicalCores", "totalRamBytes", "jdk", "os", "utcDate");
    private static final String RESULTS_COLUMN_HEADER = "benchmark,family,parameter,size,score,error,unit";
    private static final String SLOPES_COLUMN_HEADER = "family,points,empiricalSlope,theoreticalExponent";

    private final Path resultsCsv;
    private final Path slopesCsv;

    public CsvBenchmarkReportRepository(Path resultsCsv, Path slopesCsv) {
        this.resultsCsv = Objects.requireNonNull(resultsCsv, "resultsCsv");
        this.slopesCsv = Objects.requireNonNull(slopesCsv, "slopesCsv");
    }

    @Override
    public BenchmarkReport load() {
        List<String> resultsLines = readLines(resultsCsv);

        Map<String, String> harnessFields = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < resultsLines.size() && resultsLines.get(cursor).startsWith("#")) {
            parseHeaderLine(resultsLines.get(cursor), resultsCsv, harnessFields);
            cursor++;
        }
        BenchmarkHarness harness = toHarness(harnessFields, resultsCsv);

        cursor = expectColumnHeader(resultsLines, cursor, RESULTS_COLUMN_HEADER, resultsCsv);
        List<BenchmarkResult> results = new ArrayList<>();
        for (int i = cursor; i < resultsLines.size(); i++) {
            String line = resultsLines.get(i);
            if (line.isBlank()) {
                continue;
            }
            results.add(toResult(line, i + 1, resultsCsv));
        }

        List<String> slopesLines = readLines(slopesCsv);
        int slopesCursor = expectColumnHeader(slopesLines, 0, SLOPES_COLUMN_HEADER, slopesCsv);
        List<BenchmarkSlope> slopes = new ArrayList<>();
        for (int i = slopesCursor; i < slopesLines.size(); i++) {
            String line = slopesLines.get(i);
            if (line.isBlank()) {
                continue;
            }
            slopes.add(toSlope(line, i + 1, slopesCsv));
        }

        return new BenchmarkReport(harness, results, slopes);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw failure(path, "is missing");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + path + "; re-run " + EXPORT_COMMAND, e);
        }
    }

    /** Parses a {@code "# harness.<key> = <value>"} line into {@code target}. */
    private static void parseHeaderLine(String line, Path path, Map<String, String> target) {
        String withoutHash = line.substring(1).strip();
        int dot = withoutHash.indexOf('.');
        int equals = withoutHash.indexOf('=');
        if (!withoutHash.startsWith("harness.") || dot < 0 || equals < 0 || equals < dot) {
            throw failure(path, "has an unrecognized header line: '" + line + "'");
        }
        String key = withoutHash.substring(dot + 1, equals).strip();
        String value = withoutHash.substring(equals + 1).strip();
        target.put(key, value);
    }

    private static BenchmarkHarness toHarness(Map<String, String> fields, Path path) {
        for (String key : HARNESS_KEYS) {
            if (!fields.containsKey(key)) {
                throw failure(path, "is missing header key 'harness." + key + "'");
            }
        }
        return new BenchmarkHarness(
                fields.get("cpuModel"),
                parseInt(fields.get("logicalCores"), path, "harness.logicalCores"),
                parseLong(fields.get("totalRamBytes"), path, "harness.totalRamBytes"),
                fields.get("jdk"),
                fields.get("os"),
                fields.get("utcDate"));
    }

    private static String field(int lineNumber, String name) {
        return "line " + lineNumber + " field '" + name + "'";
    }

    private static int expectColumnHeader(List<String> lines, int cursor, String expected, Path path) {
        if (cursor >= lines.size() || !lines.get(cursor).equals(expected)) {
            throw failure(path, "is missing the expected column header '" + expected + "'");
        }
        return cursor + 1;
    }

    private static BenchmarkResult toResult(String line, int lineNumber, Path path) {
        String[] columns = splitColumns(line, 7, lineNumber, path);
        return new BenchmarkResult(
                columns[0], columns[1], columns[2],
                parseDouble(columns[3], path, field(lineNumber, "size")),
                parseDouble(columns[4], path, field(lineNumber, "score")),
                parseDouble(columns[5], path, field(lineNumber, "error")),
                columns[6]);
    }

    private static BenchmarkSlope toSlope(String line, int lineNumber, Path path) {
        String[] columns = splitColumns(line, 4, lineNumber, path);
        return new BenchmarkSlope(
                columns[0],
                parseInt(columns[1], path, field(lineNumber, "points")),
                parseDouble(columns[2], path, field(lineNumber, "empiricalSlope")),
                parseDouble(columns[3], path, field(lineNumber, "theoreticalExponent")));
    }

    /** No CSV quoting is needed (see class Javadoc), so a plain comma split is exact. */
    private static String[] splitColumns(String line, int expectedCount, int lineNumber, Path path) {
        String[] columns = line.split(",", -1);
        if (columns.length != expectedCount) {
            throw failure(path, "line " + lineNumber + " has " + columns.length + " columns, expected "
                    + expectedCount + ": '" + line + "'");
        }
        return columns;
    }

    /** {@code context} is either {@code "harness.<key>"} or {@link #field(int, String)}'s output. */
    private static int parseInt(String value, Path path, String context) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw failure(path, context + " has a non-numeric value: '" + value + "'");
        }
    }

    private static long parseLong(String value, Path path, String context) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw failure(path, context + " has a non-numeric value: '" + value + "'");
        }
    }

    private static double parseDouble(String value, Path path, String context) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw failure(path, context + " has a non-numeric value: '" + value + "'");
        }
    }

    private static IllegalStateException failure(Path path, String reason) {
        return new IllegalStateException(path + " " + reason + "; re-run " + EXPORT_COMMAND);
    }
}
