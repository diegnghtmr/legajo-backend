package co.edu.uniquindio.legajo.infrastructure.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkHarness;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReport;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkResult;
import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkSlope;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
 * <p>Every failure mode (missing or unreadable file, a header line missing, duplicating, or
 * not recognizing one of the six harness keys, a data row with the wrong column count, a blank
 * required field, a non-numeric or non-finite value where a number is expected, or a file with
 * a column header but zero data rows) throws {@link IllegalStateException} naming the
 * offending file and the export command to re-run ({@value #EXPORT_COMMAND}) — the same
 * fail-closed contract {@code EmbeddingCacheStartupValidator} already applies to a
 * missing/malformed embedding cache (TRD §9): the versioned results files are deployment data,
 * and an incomplete or corrupt one must stop the server from starting, never serve a partial or
 * wrong report.
 */
public final class CsvBenchmarkReportRepository implements BenchmarkReportRepository {

    static final String EXPORT_COMMAND = "./gradlew :benchmarks:jmh :benchmarks:jmhExport";

    private static final Set<String> HARNESS_KEYS =
            Set.of("cpuModel", "logicalCores", "totalRamBytes", "jdk", "os", "utcDate");
    private static final String RESULTS_COLUMN_HEADER = "benchmark,family,parameter,size,score,error,unit";
    private static final String SLOPES_COLUMN_HEADER = "family,points,empiricalSlope,theoreticalExponent";

    /**
     * A plain decimal number: optional sign, digits, optional fractional part, optional
     * exponent — exactly what {@code JmhResultsCsvWriter}'s {@code Double.toString} and
     * {@code SlopesCsvWriter}'s {@code "%.6f"} ever emit. Deliberately narrower than
     * {@link Double#parseDouble(String)}, which also accepts hex floating-point literals
     * ({@code 0x1.8p3}), a trailing {@code d}/{@code D}/{@code f}/{@code F} suffix, the literal
     * words {@code NaN}/{@code Infinity}/{@code -Infinity}, and leading/trailing whitespace —
     * none of which a well-formed export ever writes, so every one of them is treated as
     * corruption (R3-nonfinite-doubles-accepted / R3-nan-score-error).
     */
    private static final Pattern STRICT_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

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
        if (results.isEmpty()) {
            throw failure(resultsCsv, "has a column header but no data rows");
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
        if (slopes.isEmpty()) {
            throw failure(slopesCsv, "has a column header but no data rows");
        }

        return new BenchmarkReport(harness, results, slopes);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw failure(path, "is missing");
        } catch (MalformedInputException e) {
            throw failure(path, "is not valid UTF-8", e);
        } catch (IOException e) {
            throw failure(path, "could not be read (" + e.getMessage() + ")", e);
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
        if (!HARNESS_KEYS.contains(key)) {
            throw failure(path, "has an unrecognized header key 'harness." + key + "'");
        }
        if (target.containsKey(key)) {
            throw failure(path, "has a duplicate header key 'harness." + key + "'");
        }
        target.put(key, value);
    }

    private static BenchmarkHarness toHarness(Map<String, String> fields, Path path) {
        for (String key : HARNESS_KEYS) {
            if (!fields.containsKey(key)) {
                throw failure(path, "is missing header key 'harness." + key + "'");
            }
        }
        return new BenchmarkHarness(
                requireNonBlank(fields.get("cpuModel"), path, "harness.cpuModel"),
                parseInt(fields.get("logicalCores"), path, "harness.logicalCores"),
                parseLong(fields.get("totalRamBytes"), path, "harness.totalRamBytes"),
                requireNonBlank(fields.get("jdk"), path, "harness.jdk"),
                requireNonBlank(fields.get("os"), path, "harness.os"),
                requireNonBlank(fields.get("utcDate"), path, "harness.utcDate"));
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
                requireNonBlank(columns[0], path, field(lineNumber, "benchmark")),
                requireNonBlank(columns[1], path, field(lineNumber, "family")),
                requireNonBlank(columns[2], path, field(lineNumber, "parameter")),
                parseDouble(columns[3], path, field(lineNumber, "size")),
                parseDouble(columns[4], path, field(lineNumber, "score")),
                parseDouble(columns[5], path, field(lineNumber, "error")),
                requireNonBlank(columns[6], path, field(lineNumber, "unit")));
    }

    private static BenchmarkSlope toSlope(String line, int lineNumber, Path path) {
        String[] columns = splitColumns(line, 4, lineNumber, path);
        return new BenchmarkSlope(
                requireNonBlank(columns[0], path, field(lineNumber, "family")),
                parseInt(columns[1], path, field(lineNumber, "points")),
                parseDouble(columns[2], path, field(lineNumber, "empiricalSlope")),
                parseDouble(columns[3], path, field(lineNumber, "theoreticalExponent")));
    }

    /** {@code context} is either {@code "harness.<key>"} or {@link #field(int, String)}'s output. */
    private static String requireNonBlank(String value, Path path, String context) {
        if (value.isBlank()) {
            throw failure(path, context + " is blank");
        }
        return value;
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

    /**
     * Accepts only a plain decimal ({@link #STRICT_DECIMAL}) and then requires it to be finite:
     * a well-formed export never writes {@code NaN}, {@code Infinity}, a hex float literal, a
     * {@code d}/{@code f} suffix, surrounding whitespace, or a decimal so large it overflows to
     * an infinite {@code double} (R3-nonfinite-doubles-accepted / R3-nan-score-error).
     */
    private static double parseDouble(String value, Path path, String context) {
        if (!STRICT_DECIMAL.matcher(value).matches()) {
            throw failure(path, context + " has a non-numeric value: '" + value + "'");
        }
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) {
            throw failure(path, context + " has a non-finite value: '" + value + "'");
        }
        return parsed;
    }

    private static IllegalStateException failure(Path path, String reason) {
        return new IllegalStateException(path + " " + reason + "; re-run " + EXPORT_COMMAND);
    }

    private static IllegalStateException failure(Path path, String reason, Throwable cause) {
        return new IllegalStateException(path + " " + reason + "; re-run " + EXPORT_COMMAND, cause);
    }
}
