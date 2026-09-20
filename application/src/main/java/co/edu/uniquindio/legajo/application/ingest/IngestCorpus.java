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
 *
 * <p><b>Id width (explicit, deterministic rule).</b> Ids are {@code "d" + i} zero-padded
 * to {@code max(2, digitCountOf(sourceCount))}, where {@code i} runs 1..{@code
 * sourceCount} in sorted-filename order. The floor of 2 keeps the reference corpus (20
 * documents) at {@code d01..d20} and never regresses to a single digit for a small
 * folder; the width only grows past 2 once a folder yields more than 99 PDFs (100 PDFs
 * → {@code d001..d100}), which is required so {@link CorpusHasher#corpusSha256}'s
 * ascending-{@code id}-order convention stays a correct numeric order within that
 * corpus (unpadded ids would sort {@code d1, d10, d2, ...} lexicographically). This
 * rule has been in place since T4 and does not change any id in the committed
 * {@code data/corpus.json} (20 documents → width 2, same as today).
 *
 * <p><b>Fails closed on an empty input (robustness advisory).</b> An input folder with
 * zero PDFs is refused with {@link IllegalStateException} before {@link
 * CorpusRepository#save} is ever called, because {@code save} unconditionally replaces
 * the previous corpus — running ingestion against an empty or misspelled folder must
 * never silently destroy a committed, author-validated {@code corpus.json}. This class
 * intentionally does <b>not</b> also enforce the TRD §6.1 "at least 3 documents"
 * minimum: that invariant is {@link co.edu.uniquindio.legajo.corpus.CorpusVerifier}'s
 * {@code MINIMUM_DOCUMENT_COUNT} rule, the designated gate for whether a corpus is
 * acceptable to use, run explicitly via {@code verify-corpus} after ingestion and
 * before any consumer trusts the corpus. Duplicating it here would reject the very
 * small folders this reusable, "any folder of PDFs" pipeline is legitimately exercised
 * against in tests (see {@code IngestCorpusTest}, which ingests folders of 2 and 3
 * PDFs) and would blur which layer owns the invariant, without closing any additional
 * gap: nothing ever consumes a corpus without going through {@code verify-corpus} or
 * an equivalent load-time check.
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
        if (pdfFiles.isEmpty()) {
            throw new IllegalStateException(
                    ("Refusing to ingest: input folder %s contains no PDF files. Writing a corpus with "
                            + "sourceCount=0 and no documents would overwrite (CorpusRepository#save always "
                            + "replaces) whatever corpus is already there, including a committed, "
                            + "author-validated one. Check the folder path, or confirm it holds *.pdf files.")
                            .formatted(inputFolder));
        }
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
