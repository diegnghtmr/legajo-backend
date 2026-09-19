package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PdfBoxMetadataExtractor} against a tiny PDF fixture generated with PDFBox in
 * this test (never a teacher PDF, which is never committed): a title line, an authors
 * line, an "Abstract" heading, two abstract lines, and a "Keywords" heading that must
 * stop the abstract capture.
 */
class PdfBoxMetadataExtractorTest {

    private static final List<String> LINES = List.of(
            "A Tiny Paper About Testing",
            "Ada Lovelace, Grace Hopper",
            "Abstract",
            "This is the first abstract line.",
            "This is the second abstract line.",
            "Keywords",
            "testing, pdf, extraction",
            "1. Introduction",
            "This text must never appear in the extracted abstract.");

    @Test
    void extractsTitleAuthorsAndAbstractBetweenHeadingAndKeywords(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("fixture.pdf");
        writeFixturePdf(pdf);

        ExtractedPdfMetadata metadata = new PdfBoxMetadataExtractor().extract(pdf);

        assertThat(metadata.extractedBy()).isEqualTo("PDFBox");
        assertThat(metadata.title()).isEqualTo("A Tiny Paper About Testing");
        assertThat(metadata.authors()).containsExactly("Ada Lovelace", "Grace Hopper");
        assertThat(metadata.abstractText())
                .isEqualTo("This is the first abstract line. This is the second abstract line.");
        assertThat(metadata.abstractText()).doesNotContain("must never appear");
    }

    private static void writeFixturePdf(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 780);
                stream.setLeading(18);
                for (String line : LINES) {
                    stream.showText(line);
                    stream.newLine();
                }
                stream.endText();
            }

            document.save(target.toFile());
        }
    }
}
