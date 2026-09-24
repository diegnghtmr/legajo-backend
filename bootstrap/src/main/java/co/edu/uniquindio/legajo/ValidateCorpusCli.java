package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.ValidateCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point for {@code ./gradlew :bootstrap:validateCorpus --args="--ids=d01,d02"}
 * or {@code --args="--all"}. Requires one of the two flags: manual
 * validation is the only mandatory control and must be an explicit author action,
 * never a default this CLI could silently take.
 *
 * <p><b>Testable entry points.</b> {@code main} composes
 * {@link #resolveCorpusPath(String[])} (pure argument parsing) and {@link
 * #run(ValidateCorpus, Map, String, PrintStream, PrintStream)} (orchestration and exit
 * code, taking an already-constructed {@link ValidateCorpus}), so a test can exercise
 * {@code --all}, {@code --ids=...} and the missing-flag failure path against a
 * filesystem-backed {@link JsonCorpusRepository} without touching
 * {@code data/corpus.json}.
 */
public final class ValidateCorpusCli {

    private ValidateCorpusCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String corpusPath = resolveCorpusPath(options);
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(Path.of(corpusPath)));
        System.exit(run(validateCorpus, options, corpusPath, System.out, System.err));
    }

    static String resolveCorpusPath(Map<String, String> options) {
        return options.getOrDefault("corpus", "data/corpus.json");
    }

    static int run(ValidateCorpus validateCorpus, Map<String, String> options, String corpusPath, PrintStream out,
            PrintStream err) {
        Objects.requireNonNull(validateCorpus, "validateCorpus");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(corpusPath, "corpusPath");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        try {
            Corpus result;
            if (options.containsKey("all")) {
                result = validateCorpus.validateAll();
            } else if (options.containsKey("ids")) {
                Set<String> ids = Arrays.stream(options.get("ids").split(","))
                        .map(String::trim)
                        .filter(id -> !id.isBlank())
                        .collect(Collectors.toSet());
                result = validateCorpus.validateIds(ids);
            } else {
                err.println("validateCorpus requires --ids=d01,d02 or --all");
                return 1;
            }

            long validatedCount = result.documents().stream().filter(CorpusDocument::manuallyValidated).count();
            out.printf("Validated. %d/%d document(s) now have manuallyValidated=true in %s%n",
                    validatedCount, result.documents().size(), corpusPath);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("validateCorpus failed: " + e.getMessage());
            return 1;
        }
    }
}
