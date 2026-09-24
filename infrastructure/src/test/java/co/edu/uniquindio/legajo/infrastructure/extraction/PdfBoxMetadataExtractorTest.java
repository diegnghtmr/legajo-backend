package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;
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

    /**
     * Reproduces the reference corpus's own layout that used to truncate the
     * abstract — a narrow left-hand affiliations sidebar sitting beside a much wider
     * right-hand block that holds the "Abstract" heading and its full text, both
     * starting at the same page height. With the plain {@code PDFTextStripper} used
     * before this task, the sidebar and the abstract block landed on the same output
     * lines (same y, sorted left to right), so the "^abstract" heading regex never saw
     * a clean line start and the captured text was cut short. This test pins the fix:
     * the full abstract must come back intact, with no sidebar text mixed in, and
     * capture must still stop at "Keywords" exactly like the single-column case.
     */
    @Test
    void extractsCompleteAbstractFromATwoColumnSidebarLayout(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("two-column.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Full-width header lines (well wider than either column below them).
            writeAt(document, page,
                    "This Title Line Is Deliberately Long So It Spans Nearly The Whole Page Width",
                    50, 780);
            writeAt(document, page,
                    "Author One and Author Two and Author Three write this paper together here",
                    50, 760);

            // Narrow left-hand sidebar, sharing its row heights with the right column.
            writeAt(document, page, "1 University Alpha", 50, 700);
            writeAt(document, page, "Street Address One", 50, 680);
            writeAt(document, page, "2 University Beta", 50, 660);

            // Wide right-hand block: heading, then the abstract body, then Keywords.
            writeAt(document, page, "Abstract", 280, 700);
            writeAt(document, page, "This right column abstract line one continues the text", 280, 680);
            writeAt(document, page, "this right column abstract line two continues further along", 280, 660);
            writeAt(document, page, "this right column abstract line three keeps on going here", 280, 640);
            writeAt(document, page, "this right column abstract line four ends here now.", 280, 620);
            writeAt(document, page, "Keywords", 280, 600);
            writeAt(document, page, "This text must never appear in the extracted abstract.", 280, 580);

            document.save(pdf.toFile());
        }

        ExtractedPdfMetadata metadata = new PdfBoxMetadataExtractor().extract(pdf);

        assertThat(metadata.abstractText()).contains("line one continues the text");
        assertThat(metadata.abstractText()).contains("ends here now.");
        assertThat(metadata.abstractText()).doesNotContain("University");
        assertThat(metadata.abstractText()).doesNotContain("Address");
        assertThat(metadata.abstractText()).doesNotContain("must never appear");
    }

    /**
     * Some journals (the reference corpus's own two-column PDF among them) render
     * a section heading in a letter-spaced display style ("K E Y W O R D S" instead of
     * "Keywords") purely as typography — a single space between every letter, not a
     * real word break. Before this fix, {@code NEXT_SECTION_HEADING} never matched that
     * literal text, so the abstract capture ran straight through it and swallowed the
     * rest of the document.
     */
    @Test
    void stopsAbstractCaptureAtALetterSpacedKeywordsHeading(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("letter-spaced-heading.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            writeAt(document, page, "A Tiny Paper About Testing", 50, 780);
            writeAt(document, page, "Ada Lovelace, Grace Hopper", 50, 760);
            writeAt(document, page, "Abstract", 50, 740);
            writeAt(document, page, "This is the only abstract line worth keeping.", 50, 720);
            writeAt(document, page, "K E Y W O R D S", 50, 700);
            writeAt(document, page, "This text must never appear in the extracted abstract.", 50, 680);

            document.save(pdf.toFile());
        }

        ExtractedPdfMetadata metadata = new PdfBoxMetadataExtractor().extract(pdf);

        assertThat(metadata.abstractText()).isEqualTo("This is the only abstract line worth keeping.");
        assertThat(metadata.abstractText()).doesNotContain("must never appear");
    }

    /**
     * The unbounded-capture guard: when the "Abstract" heading is found but no
     * recognized next-section heading ever follows (a document with no "Keywords" or
     * "Introduction" line the regex recognizes, or one whose OCR/layout never renders
     * one cleanly), the heuristic used to capture every remaining line all the way to
     * the end of the document. This test builds many pages of filler text after the
     * heading, with no next-section heading anywhere, and pins that the captured
     * abstract stays bounded well below the filler's total length instead of running
     * to the end — {@link AbstractQualityCheck} is left to flag the (necessarily
     * incomplete, unpunctuated) result as suspicious for manual review, exactly as it
     * already does for a truncated abstract.
     */
    @Test
    void boundsAbstractCaptureWhenNoNextSectionHeadingEverFollows(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("unbounded.pdf");
        String farMarker = "THIS TEXT MUST NEVER BE REACHED BY A BOUNDED CAPTURE";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            writeAt(document, page, "A Tiny Paper With No Closing Heading", 50, 780);
            writeAt(document, page, "Author One", 50, 760);
            writeAt(document, page, "Abstract", 50, 740);

            // Comfortably more filler text than any bound should ever allow through.
            float y = 720;
            for (int i = 0; i < 400; i++) {
                writeAt(document, page,
                        "filler abstract sentence number " + i + " keeps the body going without a heading",
                        50, y);
                y -= 15;
                if (y < 40) {
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    y = 780;
                }
            }
            writeAt(document, page, farMarker, 50, Math.max(y, 20));

            document.save(pdf.toFile());
        }

        ExtractedPdfMetadata metadata = new PdfBoxMetadataExtractor().extract(pdf);

        assertThat(metadata.abstractText().length()).isLessThan(10_000);
        assertThat(metadata.abstractText()).doesNotContain(farMarker);
        assertThat(AbstractQualityCheck.assess(metadata.abstractText()).suspicious()).isTrue();
    }

    private static void writeAt(PDDocument document, PDPage page, String text, float x, float y) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.setTextMatrix(Matrix.getTranslateInstance(x, y));
            stream.showText(text);
            stream.endText();
        }
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
