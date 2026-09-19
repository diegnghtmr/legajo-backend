package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.VerifyCorpus;
import co.edu.uniquindio.legajo.corpus.CorpusVerificationResult;
import co.edu.uniquindio.legajo.corpus.CorpusViolation;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for {@code ./gradlew :bootstrap:verifyCorpus [--args="--corpus=..."]}
 * (TRD §6.1, item 6): prints every violation found (never just the first) and exits
 * non-zero if the corpus is invalid, so it can gate a CI or release step.
 */
public final class VerifyCorpusCli {

    private VerifyCorpusCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String corpusPath = options.getOrDefault("corpus", "data/corpus.json");

        VerifyCorpus verifyCorpus = new VerifyCorpus(new JsonCorpusRepository(Path.of(corpusPath)));
        CorpusVerificationResult result = verifyCorpus.verify();

        if (result.isValid()) {
            System.out.println("verify-corpus: OK (" + corpusPath + ")");
            return;
        }

        System.err.println("verify-corpus: " + result.violations().size() + " violation(s) in " + corpusPath);
        for (CorpusViolation violation : result.violations()) {
            String scope = violation.isCorpusLevel() ? "corpus" : violation.documentId();
            System.err.printf("  [%s] %s: %s%n", scope, violation.rule(), violation.message());
        }
        System.exit(1);
    }
}
