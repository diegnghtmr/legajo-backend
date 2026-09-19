package co.edu.uniquindio.legajo.infrastructure.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ColumnAwareTextExtractor} in isolation, ahead of the end-to-end
 * {@link PdfBoxMetadataExtractorTest} coverage (T4c). PDFBox's default {@code
 * PDFTextStripper} with {@code setSortByPosition(true)} sorts every character on a page
 * primarily by y then x, so two side-by-side blocks of text that share the same
 * vertical band get merged onto the very same output line instead of staying two
 * separate blocks. This class re-emits a page's text one column at a time (left to
 * right, each column top to bottom) whenever the page's body is laid out that way, and
 * leaves a single-column page byte-for-byte identical to what {@code PDFTextStripper}
 * would already produce.
 */
class ColumnAwareTextExtractorTest {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    @Test
    void singleColumnPageMatchesThePlainStripper(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("single-column.pdf");
        writePdf(pdf, (document, page) -> {
            writeLine(document, page, "First line of a single column of text.", 50, 780);
            writeLine(document, page, "Second line of the very same column.", 50, 760);
            writeLine(document, page, "Third and final line of that column.", 50, 740);
        });

        String columnAware;
        String plain;
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            columnAware = ColumnAwareTextExtractor.extractText(document);
        }
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            plain = stripper.getText(document);
        }

        assertThat(columnAware).isEqualTo(plain);
    }

    @Test
    void twoColumnPageEmitsEachColumnAsAContiguousBlock(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("two-column.pdf");
        writePdf(pdf, (document, page) -> {
            // Left and right column lines share the same three row heights, exactly the
            // layout that makes the plain sortByPosition stripper interleave them onto
            // shared output lines.
            writeLine(document, page, "Left column row one", 50, 700);
            writeLine(document, page, "Left column row two", 50, 680);
            writeLine(document, page, "Left column row three", 50, 660);

            writeLine(document, page, "Right column row one", 280, 700);
            writeLine(document, page, "Right column row two", 280, 680);
            writeLine(document, page, "Right column row three", 280, 660);
        });

        String text;
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            text = ColumnAwareTextExtractor.extractText(document);
        }

        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        int leftFirst = indexOfLineContaining(lines, "Left column row one");
        int leftLast = indexOfLineContaining(lines, "Left column row three");
        int rightFirst = indexOfLineContaining(lines, "Right column row one");
        int rightLast = indexOfLineContaining(lines, "Right column row three");

        assertThat(leftFirst).isGreaterThanOrEqualTo(0);
        assertThat(rightFirst).isGreaterThanOrEqualTo(0);
        // Each column's rows stay contiguous: nothing from the other column sits between
        // a column's first and last row.
        assertThat(leftLast - leftFirst).isEqualTo(2);
        assertThat(rightLast - rightFirst).isEqualTo(2);
        // The whole left column is emitted before the whole right column.
        assertThat(leftLast).isLessThan(rightFirst);
    }

    @Test
    void columnContentSpillsFromOnePageIntoTheMatchingColumnOfTheNext(@TempDir Path tempDir) throws IOException {
        Path pdf = tempDir.resolve("two-page-two-column.pdf");
        try (PDDocument document = new PDDocument()) {
            addPage(document, (doc, page) -> {
                writeLine(doc, page, "Sidebar line one", 50, 700);
                writeLine(doc, page, "Sidebar line two", 50, 680);
                writeLine(doc, page, "Body start of a long right column paragraph", 280, 700);
                writeLine(doc, page, "that keeps going all the way to the bottom", 280, 680);
            });
            addPage(document, (doc, page) -> {
                // Kept short enough to stay inside the left column's own x-range (as a
                // real sidebar-width column would): a line this narrow must not spill
                // into the right column's x-range and hide the gap between them.
                writeLine(doc, page, "continues right here", 50, 780);
                writeLine(doc, page, "reaches its natural end", 50, 760);
                writeLine(doc, page, "Unrelated second column content over here", 280, 780);
                writeLine(doc, page, "and a second unrelated line right below it", 280, 760);
            });
            document.save(pdf.toFile());
        }

        String text;
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            text = ColumnAwareTextExtractor.extractText(document);
        }

        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        int bodyEnd = indexOfLineContaining(lines, "that keeps going all the way to the bottom");
        int continuation = indexOfLineContaining(lines, "continues right here");
        int naturalEnd = indexOfLineContaining(lines, "reaches its natural end");
        int unrelated = indexOfLineContaining(lines, "Unrelated second column content over here");

        assertThat(bodyEnd).isGreaterThanOrEqualTo(0);
        assertThat(continuation).isGreaterThanOrEqualTo(0);
        // Page 1's right column is immediately followed by page 2's first column (the
        // paragraph's continuation), not by page 2's other column.
        assertThat(continuation).isEqualTo(bodyEnd + 1);
        assertThat(naturalEnd).isEqualTo(continuation + 1);
        assertThat(unrelated).isGreaterThan(naturalEnd);
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private interface PageWriter {
        void write(PDDocument document, PDPage page) throws IOException;
    }

    private static void writePdf(Path target, PageWriter writer) throws IOException {
        try (PDDocument document = new PDDocument()) {
            addPage(document, writer);
            document.save(target.toFile());
        }
    }

    private static void addPage(PDDocument document, PageWriter writer) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        document.addPage(page);
        writer.write(document, page);
    }

    /** Places one line of text with its baseline's lower-left corner at the exact PDF
     * user-space coordinates (x, y), independent of any other line on the page. */
    private static void writeLine(PDDocument document, PDPage page, String text, float x, float y) {
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.setTextMatrix(Matrix.getTranslateInstance(x, y));
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
    }

    private static final class UncheckedIOExceptionForTest extends RuntimeException {
        UncheckedIOExceptionForTest(IOException cause) {
            super(cause);
        }
    }
}
