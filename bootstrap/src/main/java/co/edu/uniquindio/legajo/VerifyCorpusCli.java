package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.VerifyCorpus;
import co.edu.uniquindio.legajo.corpus.CorpusVerificationResult;
import co.edu.uniquindio.legajo.corpus.CorpusViolation;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Entry point for {@code ./gradlew :bootstrap:verifyCorpus [--args="--corpus=..."]}
 * (TRD §6.1, item 6): prints every violation found (never just the first) and exits
 * non-zero if the corpus is invalid, so it can gate a CI or release step.
 *
 * <p><b>Testable entry points (CLI-contracts advisory).</b> {@code main} composes
 * {@link #resolveCorpusPath(String[])} (pure argument parsing) and {@link
 * #run(VerifyCorpus, String, PrintStream, PrintStream)} (orchestration and exit code,
 * taking an already-constructed {@link VerifyCorpus}), so a test can supply a
 * filesystem-backed {@link JsonCorpusRepository} over a temp corpus and assert both the
 * OK and the violation/non-zero-exit paths without touching {@code data/corpus.json}.
 */
public final class VerifyCorpusCli {

    private VerifyCorpusCli() {
    }

    public static void main(String[] args) {
        String corpusPath = resolveCorpusPath(args);
        VerifyCorpus verifyCorpus = new VerifyCorpus(new JsonCorpusRepository(Path.of(corpusPath)));
        System.exit(run(verifyCorpus, corpusPath, System.out, System.err));
    }

    static String resolveCorpusPath(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        return options.getOrDefault("corpus", "data/corpus.json");
    }

    static int run(VerifyCorpus verifyCorpus, String corpusPath, PrintStream out, PrintStream err) {
        Objects.requireNonNull(verifyCorpus, "verifyCorpus");
        Objects.requireNonNull(corpusPath, "corpusPath");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        CorpusVerificationResult result = verifyCorpus.verify();

        if (result.isValid()) {
            out.println("verify-corpus: OK (" + corpusPath + ")");
            return 0;
        }

        err.println("verify-corpus: " + result.violations().size() + " violation(s) in " + corpusPath);
        for (CorpusViolation violation : result.violations()) {
            String scope = violation.isCorpusLevel() ? "corpus" : violation.documentId();
            err.printf("  [%s] %s: %s%n", scope, violation.rule(), violation.message());
        }
        return 1;
    }
}
