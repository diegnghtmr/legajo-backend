package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.ingest.IngestCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.infrastructure.corpus.JsonCorpusRepository;
import co.edu.uniquindio.legajo.infrastructure.extraction.FallbackPdfMetadataExtractor;
import co.edu.uniquindio.legajo.infrastructure.extraction.GrobidPdfMetadataExtractor;
import co.edu.uniquindio.legajo.infrastructure.extraction.PdfBoxMetadataExtractor;
import co.edu.uniquindio.legajo.port.PdfExtractionException;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for {@code ./gradlew :bootstrap:ingest --args="--input=data/pdfs
 * --output=data/corpus.json --grobid-url=http://localhost:8070"} (TRD §6.1): scans a
 * folder of PDFs, extracts each through GROBID with a PDFBox fallback, and writes
 * {@code corpus.json} with every document {@code manuallyValidated=false}.
 *
 * <p>A plain {@code main} rather than a Spring profile/{@code CommandLineRunner}: this
 * is a one-shot offline batch job with no web, scheduling or dependency-injection need,
 * so a Spring context would only add startup cost and configuration surface (documented
 * as the chosen approach in {@code backend/README.md}).
 */
public final class IngestCli {

    private IngestCli() {
    }

    public static void main(String[] args) {
        Map<String, String> options = CliArgs.parse(args);
        String input = options.getOrDefault("input", "data/pdfs");
        String output = options.getOrDefault("output", "data/corpus.json");
        String grobidUrl = options.getOrDefault("grobid-url", "http://localhost:8070");

        FallbackPdfMetadataExtractor extractor = new FallbackPdfMetadataExtractor(
                new GrobidPdfMetadataExtractor(grobidUrl), new PdfBoxMetadataExtractor());
        JsonCorpusRepository repository = new JsonCorpusRepository(Path.of(output));
        IngestCorpus ingestCorpus = new IngestCorpus(extractor, repository);

        try {
            Corpus corpus = ingestCorpus.ingest(Path.of(input), "1.0");
            System.out.printf("Ingested %d document(s) from %s into %s%n", corpus.sourceCount(), input, output);
            for (CorpusDocument document : corpus.documents()) {
                System.out.printf("  %s [%s] %s%n", document.id(), document.extractedBy(), document.title());
            }
            System.out.println("Every document has manuallyValidated=false; review each abstract, then run "
                    + "validateCorpus (TRD §6.1, item 5).");
        } catch (PdfExtractionException e) {
            System.err.println("Ingestion failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
