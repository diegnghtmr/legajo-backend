package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.IngestCorpus;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ingest}'s argument contract and exit-code contract.
 * {@link IngestCli#main} wires real GROBID/PDFBox extractors and needs a live GROBID to
 * exercise end to end, so this exercises the two testable seams it composes:
 * {@link IngestCli#resolveOptions(String[])} (pure argument parsing) and
 * {@link IngestCli#run(IngestCorpus, Path, String, PrintStream, PrintStream)}
 * (orchestration and exit code) against a fake extractor and a real, filesystem-backed
 * {@link JsonCorpusRepository} — no network involved.
 */
class IngestCliTest {

    private static final PdfMetadataExtractor FAKE_EXTRACTOR = pdfPath -> new ExtractedPdfMetadata(
            "Title for " + pdfPath.getFileName(), List.of("Author"), "Abstract for " + pdfPath.getFileName(),
            "FAKE");

    /** No process environment at all, so these tests never depend on the machine they run on. */
    private static final UnaryOperator<String> NO_ENVIRONMENT = name -> null;

    @Test
    void resolveOptionsAppliesDefaultsWhenNoArgumentsAreGiven() {
        IngestCli.Options options = IngestCli.resolveOptions(new String[0], NO_ENVIRONMENT);

        assertThat(options.input()).isEqualTo("data/pdfs");
        assertThat(options.output()).isEqualTo("data/corpus.json");
        assertThat(options.grobidUrl()).isEqualTo("http://localhost:8070");
    }

    @Test
    void resolveOptionsHonorsExplicitOverrides() {
        IngestCli.Options options = IngestCli.resolveOptions(new String[] {
                "--input=custom/pdfs", "--output=custom/corpus.json", "--grobid-url=http://grobid.example:9000"},
                NO_ENVIRONMENT);

        assertThat(options.input()).isEqualTo("custom/pdfs");
        assertThat(options.output()).isEqualTo("custom/corpus.json");
        assertThat(options.grobidUrl()).isEqualTo("http://grobid.example:9000");
    }

    /**
     * {@code LEGAJO_GROBID_URL} is the GROBID endpoint for the ingest
     * profile, and {@code .env.example} declares it. It was read by nothing until now, so
     * setting it in a real {@code .env} silently had no effect.
     */
    @Test
    void resolveOptionsTakesTheGrobidUrlFromTheEnvironmentWhenNoArgumentIsGiven() {
        IngestCli.Options options = IngestCli.resolveOptions(new String[0],
                environment(Map.of("LEGAJO_GROBID_URL", "http://grobid.internal:8070")));

        assertThat(options.grobidUrl()).isEqualTo("http://grobid.internal:8070");
    }

    /** An explicit flag is the more specific instruction, so it wins over the environment. */
    @Test
    void anExplicitGrobidUrlArgumentWinsOverTheEnvironment() {
        IngestCli.Options options = IngestCli.resolveOptions(new String[] {"--grobid-url=http://from.flag:9000"},
                environment(Map.of("LEGAJO_GROBID_URL", "http://from.env:8070")));

        assertThat(options.grobidUrl()).isEqualTo("http://from.flag:9000");
    }

    /**
     * A {@code .env} line written as {@code LEGAJO_GROBID_URL=} exports an empty string, not
     * an absent variable. Treating that as a URL would point ingestion at "" and fail with a
     * confusing connection error instead of falling back to the documented default.
     */
    @Test
    void aBlankEnvironmentValueFallsBackToTheDefault() {
        IngestCli.Options options = IngestCli.resolveOptions(new String[0],
                environment(Map.of("LEGAJO_GROBID_URL", "   ")));

        assertThat(options.grobidUrl()).isEqualTo("http://localhost:8070");
    }

    private static UnaryOperator<String> environment(Map<String, String> variables) {
        return variables::get;
    }

    @Test
    void runReturnsZeroAndWritesTheCorpusOnSuccess(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("pdfs");
        Files.createDirectories(inputFolder);
        Files.createFile(inputFolder.resolve("01.pdf"));
        Path outputPath = tempDir.resolve("corpus.json");
        IngestCorpus ingestCorpus = new IngestCorpus(FAKE_EXTRACTOR, new JsonCorpusRepository(outputPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = IngestCli.run(ingestCorpus, inputFolder, outputPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isZero();
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(outBuffer.toString(StandardCharsets.UTF_8))
                .contains("Ingested 1 document(s)")
                .contains("d01")
                .contains("Abstract quality summary:");
        assertThat(Files.exists(outputPath)).isTrue();
    }

    /**
     * The empty-input-folder robustness fix ({@code IngestCorpus} now fails closed with
     * {@link IllegalStateException} rather than an unrelated {@code
     * PdfExtractionException}) must still surface as a clean non-zero exit with a
     * message, not an uncaught stack trace — this pins {@code run}'s catch to every
     * {@code RuntimeException} the use case can throw, not only {@code
     * PdfExtractionException}.
     */
    @Test
    void runReturnsOneAndPrintsTheFailureMessageWhenIngestionFailsClosed(@TempDir Path tempDir) throws IOException {
        Path emptyInputFolder = tempDir.resolve("empty");
        Files.createDirectories(emptyInputFolder);
        Path outputPath = tempDir.resolve("corpus.json");
        IngestCorpus ingestCorpus = new IngestCorpus(FAKE_EXTRACTOR, new JsonCorpusRepository(outputPath));
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

        int exitCode = IngestCli.run(ingestCorpus, emptyInputFolder, outputPath.toString(),
                new PrintStream(outBuffer, true, StandardCharsets.UTF_8), new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

        assertThat(exitCode).isEqualTo(1);
        assertThat(errBuffer.toString(StandardCharsets.UTF_8)).contains("Ingestion failed:");
        assertThat(Files.exists(outputPath)).isFalse();
    }
}
