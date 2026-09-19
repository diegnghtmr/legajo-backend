package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.port.CorpusRepository;
import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The offline ingestion pipeline of TRD §6.1: scan a folder of PDFs sorted by name,
 * extract each one through {@link PdfMetadataExtractor} (a GROBID/PDFBox fallback
 * chain in production, task T4's infrastructure), assign {@code d01..dNN} ids in that
 * order, and write the result through {@link CorpusRepository} — replacing whatever
 * corpus was there before (TRD §3.1: re-running ingestion on any folder produces a new
 * {@code corpus.json}).
 *
 * <p>Every document is written with {@code manuallyValidated = false}: TRD §6.1, item 5
 * makes manual validation "the only mandatory control", performed later and only by an
 * explicit call to {@link ValidateCorpus} (constraint reinforced in the feature doc:
 * "never set by the agent on its own").
 */
public final class IngestCorpus {

    private final PdfMetadataExtractor extractor;
    private final CorpusRepository repository;

    public IngestCorpus(PdfMetadataExtractor extractor, CorpusRepository repository) {
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Corpus ingest(Path inputFolder, String corpusVersion) {
        Objects.requireNonNull(inputFolder, "inputFolder");
        Objects.requireNonNull(corpusVersion, "corpusVersion");

        List<Path> pdfFiles = listPdfsSortedByName(inputFolder);
        int sourceCount = pdfFiles.size();
        int idWidth = Math.max(2, String.valueOf(sourceCount).length());

        List<CorpusDocument> documents = new java.util.ArrayList<>(sourceCount);
        for (int i = 0; i < pdfFiles.size(); i++) {
            Path pdfFile = pdfFiles.get(i);
            String id = "d" + String.format(Locale.ROOT, "%0" + idWidth + "d", i + 1);
            documents.add(toDocument(id, inputFolder, pdfFile));
        }

        String corpusSha256 = CorpusHasher.corpusSha256(documents);
        Corpus corpus = new Corpus(corpusVersion, sourceCount, corpusSha256, documents);
        repository.save(corpus);
        return corpus;
    }

    private CorpusDocument toDocument(String id, Path inputFolder, Path pdfFile) {
        ExtractedPdfMetadata metadata = extractor.extract(pdfFile);
        String source = inputFolder.toString().replace('\\', '/') + "/" + pdfFile.getFileName();
        String abstractSha256 = CorpusHasher.abstractSha256(metadata.abstractText());

        return new CorpusDocument(id, metadata.title(), metadata.authors(), metadata.abstractText(), source,
                metadata.extractedBy(), false, abstractSha256);
    }

    private static List<Path> listPdfsSortedByName(Path inputFolder) {
        try (Stream<Path> entries = Files.list(inputFolder)) {
            return entries
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list PDFs in " + inputFolder, e);
        }
    }
}
