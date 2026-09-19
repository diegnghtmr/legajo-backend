package co.edu.uniquindio.legajo.infrastructure.corpus;

import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.corpus.CorpusDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip and on-disk shape tests for {@link JsonCorpusRepository} against TRD §9's
 * corpus schema: field order, pretty printing, UTF-8, and a trailing newline.
 */
class JsonCorpusRepositoryTest {

    @Test
    void savesAndLoadsAnEquivalentCorpus(@TempDir Path tempDir) {
        Path corpusPath = tempDir.resolve("corpus.json");
        JsonCorpusRepository repository = new JsonCorpusRepository(corpusPath);
        Corpus original = twoDocumentCorpus();

        repository.save(original);
        Corpus loaded = repository.load();

        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void writesUtf8PrettyPrintedJsonWithStableFieldOrderAndTrailingNewline(@TempDir Path tempDir) throws IOException {
        Path corpusPath = tempDir.resolve("corpus.json");
        JsonCorpusRepository repository = new JsonCorpusRepository(corpusPath);

        repository.save(twoDocumentCorpus());
        String content = Files.readString(corpusPath, StandardCharsets.UTF_8);

        assertThat(content).endsWith("\n").doesNotEndWith("\n\n");
        assertThat(content.indexOf("\"version\"")).isLessThan(content.indexOf("\"sourceCount\""));
        assertThat(content.indexOf("\"sourceCount\"")).isLessThan(content.indexOf("\"corpusSha256\""));
        assertThat(content.indexOf("\"corpusSha256\"")).isLessThan(content.indexOf("\"documents\""));
        assertThat(content.indexOf("\"id\"")).isLessThan(content.indexOf("\"title\""));
        assertThat(content.indexOf("\"title\"")).isLessThan(content.indexOf("\"authors\""));
        assertThat(content.indexOf("\"authors\"")).isLessThan(content.indexOf("\"abstract\""));
        assertThat(content.indexOf("\"abstract\"")).isLessThan(content.indexOf("\"source\""));
        assertThat(content.indexOf("\"source\"")).isLessThan(content.indexOf("\"extractedBy\""));
        assertThat(content.indexOf("\"extractedBy\"")).isLessThan(content.indexOf("\"manuallyValidated\""));
        assertThat(content.indexOf("\"manuallyValidated\"")).isLessThan(content.indexOf("\"abstractSha256\""));
        assertThat(content).contains("  \"version\"");
    }

    @Test
    void loadingAMissingFileFailsWithAnUncheckedIoException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.json");
        JsonCorpusRepository repository = new JsonCorpusRepository(missing);

        assertThatThrownBy(repository::load)
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    private static Corpus twoDocumentCorpus() {
        CorpusDocument d1 = new CorpusDocument("d01", "Title One", List.of("Author One"),
                "Abstract one.", "data/pdfs/01.pdf", "GROBID", true,
                "8b7df143d91c716ecfa5fc1730022f6b421b05cedee8fd52b1fc65a96030ad52");
        CorpusDocument d2 = new CorpusDocument("d02", "Title Two", List.of("Author Two", "Author Three"),
                "Abstract two.", "data/pdfs/02.pdf", "PDFBox", true,
                "1cb251ec0d568de6a929b520c4aed8d1b184b3ed4a7f6c1b1e4b8e3c1a1b1a1c");
        return new Corpus("1.0", 2, "corpus-hash-placeholder", List.of(d1, d2));
    }
}
