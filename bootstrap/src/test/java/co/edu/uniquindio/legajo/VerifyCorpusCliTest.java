package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.VerifyCorpus;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code verifyCorpus}'s argument and exit-code contract:
 * {@link VerifyCorpusCli#resolveCorpusPath(String[])} (pure argument parsing) and
 * {@link VerifyCorpusCli#run(VerifyCorpus, String, PrintStream, PrintStream)}
 * (orchestration and exit code) against a filesystem-backed {@link JsonCorpusRepository}
 * over a temp corpus — never {@code data/corpus.json}.
 */
class VerifyCorpusCliTest {

    @Test
    void resolveCorpusPathDefaultsToDataCorpusJson() {
        assertThat(VerifyCorpusCli.resolveCorpusPath(new String[0])).isEqualTo("data/corpus.json");
    }

    @Test
    void resolveCorpusPathHonorsAnExplicitOverride() {
        assertThat(VerifyCorpusCli.resolveCorpusPath(new String[] {"--corpus=custom/corpus.json"}))
                .isEqualTo("custom/corpus.json");
    }

    @Test
    void runReturnsZeroAndPrintsOkForAValidCorpus(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        new JsonCorpusRepository(corpusPath).save(validThreeDocumentCorpus());
        VerifyCorpus verifyCorpus = new VerifyCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = VerifyCorpusCli.run(verifyCorpus, corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).contains("verify-corpus: OK").contains(corpusPath.toString());
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void runReturnsOneAndListsEveryViolationForAnInvalidCorpus(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        Corpus invalidCorpus = new Corpus("1.0", 1, "hash",
                List.of(new CorpusDocument("d01", "Title", List.of("Author"), "", "data/pdfs/01.pdf", "GROBID",
                        false, CorpusHasher.abstractSha256(""))));
        new JsonCorpusRepository(corpusPath).save(invalidCorpus);
        VerifyCorpus verifyCorpus = new VerifyCorpus(new JsonCorpusRepository(corpusPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = VerifyCorpusCli.run(verifyCorpus, corpusPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(outBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).contains("violation(s)").contains(corpusPath.toString());
    }

    private static Corpus validThreeDocumentCorpus() {
        List<CorpusDocument> documents = List.of(
                document("d01", "First abstract text that is definitely non-blank for verification."),
                document("d02", "Second abstract text that is definitely non-blank for verification."),
                document("d03", "Third abstract text that is definitely non-blank for verification."));
        return new Corpus("1.0", documents.size(), CorpusHasher.corpusSha256(documents), documents);
    }

    private static CorpusDocument document(String id, String abstractText) {
        return new CorpusDocument(id, "Title " + id, List.of("Author"), abstractText,
                "data/pdfs/" + id + ".pdf", "GROBID", true, CorpusHasher.abstractSha256(abstractText));
    }
}
