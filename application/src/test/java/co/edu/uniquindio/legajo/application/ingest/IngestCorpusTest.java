package co.edu.uniquindio.legajo.application.ingest;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusHasher;
import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link IngestCorpus} against a fake {@link PdfMetadataExtractor} and an in-memory
 * repository, covering TRD §6.1 item 4's ingestion contract: scan {@code *.pdf} sorted
 * by name, assign {@code d01..dNN} ids in that order, record a {@code data/...}-style
 * relative source path, never set {@code manuallyValidated}, and compute both hashes.
 */
class IngestCorpusTest {

    private static final PdfMetadataExtractor FAKE_EXTRACTOR = pdfPath -> new ExtractedPdfMetadata(
            "Title for " + pdfPath.getFileName(),
            List.of("Author One"),
            "Abstract for " + pdfPath.getFileName(),
            "FAKE");

    @Test
    void ingestsPdfsInSortedOrderWithGeneratedIdsAndSourcePaths(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("data").resolve("pdfs");
        Files.createDirectories(inputFolder);
        Files.createFile(inputFolder.resolve("02.pdf"));
        Files.createFile(inputFolder.resolve("01.pdf"));
        Files.createFile(inputFolder.resolve("readme.txt"));

        InMemoryCorpusRepository repository = new InMemoryCorpusRepository();
        IngestCorpus ingestCorpus = new IngestCorpus(FAKE_EXTRACTOR, repository);

        Corpus corpus = ingestCorpus.ingest(inputFolder, "1.0");

        assertThat(corpus.version()).isEqualTo("1.0");
        assertThat(corpus.sourceCount()).isEqualTo(2);
        assertThat(corpus.documents()).hasSize(2);

        assertThat(corpus.documents().get(0).id()).isEqualTo("d01");
        assertThat(corpus.documents().get(0).title()).isEqualTo("Title for 01.pdf");
        assertThat(corpus.documents().get(0).source()).isEqualTo(inputFolder + "/01.pdf");
        assertThat(corpus.documents().get(0).extractedBy()).isEqualTo("FAKE");
        assertThat(corpus.documents().get(0).manuallyValidated()).isFalse();
        assertThat(corpus.documents().get(0).abstractSha256())
                .isEqualTo(CorpusHasher.abstractSha256("Abstract for 01.pdf"));

        assertThat(corpus.documents().get(1).id()).isEqualTo("d02");
        assertThat(corpus.documents().get(1).source()).isEqualTo(inputFolder + "/02.pdf");

        assertThat(corpus.corpusSha256()).isEqualTo(CorpusHasher.corpusSha256(corpus.documents()));
        assertThat(repository.saved()).isEqualTo(corpus);
    }

    @Test
    void padsIdsToTheWidthNeededForSourceCount(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("pdfs");
        Files.createDirectories(inputFolder);
        for (int i = 1; i <= 10; i++) {
            Files.createFile(inputFolder.resolve(String.format("%02d.pdf", i)));
        }

        Corpus corpus = new IngestCorpus(FAKE_EXTRACTOR, new InMemoryCorpusRepository()).ingest(inputFolder, "1.0");

        assertThat(corpus.documents()).extracting(doc -> doc.id())
                .startsWith("d01", "d02")
                .endsWith("d10");
    }

    @Test
    void neverPadsBelowTwoDigitsEvenForASingleDigitSourceCount(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("pdfs");
        Files.createDirectories(inputFolder);
        for (int i = 1; i <= 3; i++) {
            Files.createFile(inputFolder.resolve(String.format("%02d.pdf", i)));
        }

        Corpus corpus = new IngestCorpus(FAKE_EXTRACTOR, new InMemoryCorpusRepository()).ingest(inputFolder, "1.0");

        assertThat(corpus.documents()).extracting(doc -> doc.id()).containsExactly("d01", "d02", "d03");
    }

    @Test
    void widensIdsPastTwoDigitsOnceSourceCountCrossesOneHundred(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("pdfs");
        Files.createDirectories(inputFolder);
        for (int i = 1; i <= 100; i++) {
            Files.createFile(inputFolder.resolve(String.format("%03d.pdf", i)));
        }

        Corpus corpus = new IngestCorpus(FAKE_EXTRACTOR, new InMemoryCorpusRepository()).ingest(inputFolder, "1.0");

        assertThat(corpus.documents()).extracting(doc -> doc.id()).startsWith("d001", "d002").endsWith("d100");
    }

    /**
     * The most serious ingestion advisory: an input folder that yields zero PDFs (an
     * empty folder, a wrong path, or one holding only non-PDF files) must never reach
     * {@link co.edu.uniquindio.legajo.port.CorpusRepository#save}, because {@code save}
     * unconditionally replaces whatever corpus is already there — including the
     * committed, author-validated reference corpus. Ingestion must fail closed instead.
     */
    @Test
    void refusesToWriteWhenTheInputFolderHasNoPdfs(@TempDir Path tempDir) throws IOException {
        Path inputFolder = tempDir.resolve("empty");
        Files.createDirectories(inputFolder);
        Files.createFile(inputFolder.resolve("readme.txt"));
        InMemoryCorpusRepository repository = new InMemoryCorpusRepository();

        assertThatThrownBy(() -> new IngestCorpus(FAKE_EXTRACTOR, repository).ingest(inputFolder, "1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(inputFolder.toString());
        assertThat(repository.saved()).isNull();
    }
}
