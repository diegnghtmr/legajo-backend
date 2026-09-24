package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.ValidateCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code validateCorpus}'s argument and exit-code contract:
 * {@link ValidateCorpusCli#resolveCorpusPath(Map)} (pure argument parsing) and {@link
 * ValidateCorpusCli#run(ValidateCorpus, Map, String, PrintStream, PrintStream)}
 * (orchestration and exit code) against a filesystem-backed {@link JsonCorpusRepository}
 * over a temp corpus — never {@code data/corpus.json}. Covers {@code --all}, {@code
 * --ids=...}, and the "neither flag given" fail-closed contract:
 * manual validation must always be an explicit author action.
 */
class ValidateCorpusCliTest {

    @Test
    void resolveCorpusPathDefaultsToDataCorpusJson() {
        assertThat(ValidateCorpusCli.resolveCorpusPath(CliArgs.parse(new String[0]))).isEqualTo("data/corpus.json");
    }

    @Test
    void resolveCorpusPathHonorsAnExplicitOverride() {
        assertThat(ValidateCorpusCli.resolveCorpusPath(CliArgs.parse(new String[] {"--corpus=custom/corpus.json"})))
                .isEqualTo("custom/corpus.json");
    }

    @Test
    void runValidatesEveryDocumentWithTheAllFlag(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        new JsonCorpusRepository(corpusPath).save(twoUnvalidatedDocumentCorpus());
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = ValidateCorpusCli.run(validateCorpus, Map.of("all", "true"), corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("2/2 document(s)");
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void runValidatesOnlyTheNamedIds(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        new JsonCorpusRepository(corpusPath).save(twoUnvalidatedDocumentCorpus());
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = ValidateCorpusCli.run(validateCorpus, Map.of("ids", "d01"), corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("1/2 document(s)");
    }

    /**
     * The "never a default this CLI could silently take" contract: neither {@code
     * --ids} nor {@code --all} must fail closed with a clear message and a non-zero
     * exit, never validate anything.
     */
    @Test
    void runFailsClosedWhenNeitherIdsNorAllIsGiven(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        new JsonCorpusRepository(corpusPath).save(twoUnvalidatedDocumentCorpus());
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = ValidateCorpusCli.run(validateCorpus, Map.of(), corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).contains("--ids=d01,d02 or --all");
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void runFailsClosedAndReturnsOneForAnUnknownId(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        new JsonCorpusRepository(corpusPath).save(twoUnvalidatedDocumentCorpus());
        ValidateCorpus validateCorpus = new ValidateCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = ValidateCorpusCli.run(validateCorpus, Map.of("ids", "d99"), corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).contains("validateCorpus failed").contains("d99");
    }

    private static Corpus twoUnvalidatedDocumentCorpus() {
        List<CorpusDocument> documents = List.of(
                document("d01", "First abstract text that is definitely non-blank for verification."),
                document("d02", "Second abstract text that is definitely non-blank for verification."));
        return new Corpus("1.0", documents.size(), CorpusHasher.corpusSha256(documents), documents);
    }

    private static CorpusDocument document(String id, String abstractText) {
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText,
                "data/pdfs/" + id + ".pdf", "GROBID", false, CorpusHasher.abstractSha256(abstractText));
    }
}
