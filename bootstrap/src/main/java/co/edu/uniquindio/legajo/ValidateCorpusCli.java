package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.ValidateCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point for {@code ./gradlew :bootstrap:validateCorpus --args="--ids=d01,d02"}
 * or {@code --args="--all"} (TRD §6.1, item 5). Requires one of the two flags: manual
 * validation is "the only mandatory control" and must be an explicit author action,
 * never a default this CLI could silently take.
 */
public final class ValidateCorpusCli {

    private ValidateCorpusCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String corpusPath = options.getOrDefault("corpus", "data/corpus.json");
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(Path.of(corpusPath)));

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
                System.err.println("validateCorpus requires --ids=d01,d02 or --all");
                System.exit(1);
                return;
            }

            long validatedCount = result.documents().stream().filter(CorpusDocument::manuallyValidated).count();
            System.out.printf("Validated. %d/%d document(s) now have manuallyValidated=true in %s%n",
                    validatedCount, result.documents().size(), corpusPath);
        } catch (IllegalArgumentException e) {
            System.err.println("validateCorpus failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
