package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.IngestCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.extraction.FallbackPdfMetadataExtractor;
import co.edu.uniquindio.legajo.infrastructure.extraction.GrobidPdfMetadataExtractor;
import co.edu.uniquindio.legajo.infrastructure.extraction.PdfBoxMetadataExtractor;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Entry point for {@code ./gradlew :bootstrap:ingest --args="--input=data/pdfs
 * --output=data/corpus.json --grobid-url=http://localhost:8070"}: scans a
 * folder of PDFs, extracts each through GROBID with a PDFBox fallback, and writes
 * {@code corpus.json} with every document {@code manuallyValidated=false}.
 *
 * <p>A plain {@code main} rather than a Spring profile/{@code CommandLineRunner}: this
 * is a one-shot offline batch job with no web, scheduling or dependency-injection need,
 * so a Spring context would only add startup cost and configuration surface (documented
 * as the chosen approach in {@code backend/README.md}).
 *
 * <p><b>Testable entry points.</b> {@code main} wires real
 * GROBID/PDFBox extractors and can only be exercised end to end against a live GROBID
 * instance, so the argument contract and the exit-code contract are split into two
 * package-private, dependency-free-of-network methods {@code main} composes:
 * {@link #resolveOptions(String[])} (pure argument parsing/defaulting) and
 * {@link #run(IngestCorpus, Path, String, PrintStream, PrintStream)} (orchestration and
 * exit code, taking an already-constructed {@link IngestCorpus} so tests can supply a
 * fake extractor and a real, filesystem-backed repository without any network).
 */
public final class IngestCli {

    private IngestCli() {
    }

    public static void main(String[] args) {
        Options options = resolveOptions(args);
        FallbackPdfMetadataExtractor extractor = new FallbackPdfMetadataExtractor(
                new GrobidPdfMetadataExtractor(options.grobidUrl()), new PdfBoxMetadataExtractor());
        JsonCorpusRepository repository = new JsonCorpusRepository(Path.of(options.output()));
        IngestCorpus ingestCorpus = new IngestCorpus(extractor, repository);

        System.exit(run(ingestCorpus, Path.of(options.input()), options.output(), System.out, System.err));
    }

    /** The resolved {@code --input}/{@code --output}/{@code --grobid-url} arguments, with their defaults applied. */
    record Options(String input, String output, String grobidUrl) {
    }

    /** The environment variable used as the GROBID endpoint for the ingest profile. */
    static final String GROBID_URL_VARIABLE = "LEGAJO_GROBID_URL";

    private static final String DEFAULT_GROBID_URL = "http://localhost:8070";

    static Options resolveOptions(String[] args) {
        return resolveOptions(args, System::getenv);
    }

    /**
     * Resolves the options with the process environment passed in rather than read directly,
     * so tests never depend on what happens to be exported on the machine running them.
     *
     * <p>The GROBID endpoint resolves as {@code --grobid-url}, then {@value #GROBID_URL_VARIABLE},
     * then {@code http://localhost:8070}. The flag wins because it is the more specific
     * instruction. A blank environment value counts as unset: a {@code .env} line written as
     * {@code LEGAJO_GROBID_URL=} exports an empty string rather than removing the variable,
     * and pointing ingestion at "" would fail with a confusing connection error instead of
     * falling back to the documented default.
     */
    static Options resolveOptions(String[] args, UnaryOperator<String> environment) {
        Objects.requireNonNull(environment, "environment");
        Map<String, String> options = CliArgs.parse(args);
        String fromEnvironment = environment.apply(GROBID_URL_VARIABLE);
        String grobidDefault = fromEnvironment == null || fromEnvironment.isBlank()
                ? DEFAULT_GROBID_URL
                : fromEnvironment.strip();
        return new Options(
                options.getOrDefault("input", "data/pdfs"),
                options.getOrDefault("output", "data/corpus.json"),
                options.getOrDefault("grobid-url", grobidDefault));
    }

    static int run(IngestCorpus ingestCorpus, Path input, String output, PrintStream out, PrintStream err) {
        Objects.requireNonNull(ingestCorpus, "ingestCorpus");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");

        try {
            Corpus corpus = ingestCorpus.ingest(input, "1.0");
            out.printf("Ingested %d document(s) from %s into %s%n", corpus.sourceCount(), input, output);
            for (CorpusDocument document : corpus.documents()) {
                out.printf("  %s [%s] %s%n", document.id(), document.extractedBy(), document.title());
            }
            out.println("Every document has manuallyValidated=false; review each abstract, then run "
                    + "validateCorpus.");

            out.println("Abstract quality summary:");
            for (CorpusDocument document : corpus.documents()) {
                out.println(IngestQualitySummary.line(document));
            }
            return 0;
        } catch (RuntimeException e) {
            err.println("Ingestion failed: " + e.getMessage());
            return 1;
        }
    }
}
