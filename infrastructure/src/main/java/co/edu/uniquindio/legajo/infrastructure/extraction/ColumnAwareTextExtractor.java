package co.edu.uniquindio.legajo.infrastructure.extraction;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * T4c: column-aware page text extraction for {@link PdfBoxMetadataExtractor}.
 *
 * <p>{@code PDFTextStripper} with {@code setSortByPosition(true)} sorts every character
 * on a page primarily by y (top to bottom) then x (left to right). That is the right
 * order for an ordinary single-column page, but it silently corrupts a page that has a
 * narrow sidebar next to a wide text block at the same height (a title-page layout, not
 * a flowing multi-column body): the sidebar and the wide block share the same y band, so
 * their characters land on the very same output line, interleaved, and any heading regex
 * that expects a line to start with a heading word never matches. This is exactly what
 * happened to one PDF in the reference corpus, where the "Abstract" heading and its text
 * sit in a wide right-hand block next to a narrow left-hand affiliations sidebar: GROBID
 * itself under-segmented the same abstract (see the T4c task notes), and the existing
 * plain-{@code PDFTextStripper} fallback produced the same truncation for a different,
 * PDFBox-specific reason (this class's Javadoc above).
 *
 * <p>Detection is a per-page, generic geometric heuristic — nothing here is specific to
 * any one document (TC-01, reusability over any PDF folder):
 *
 * <ol>
 *   <li>Collect every glyph's horizontal extent and top-adjusted y position on the page
 *       (via {@link TextPosition}, ignoring page rotation quirks by using the
 *       "direction-adjusted" accessors PDFBox already provides for this purpose).</li>
 *   <li>Group glyphs into rows by y-proximity, split each row into segments wherever an
 *       internal x-gap is at least as wide as a column gutter (so a sidebar and a wide
 *       block sharing one row are not merged back together), and classify a segment as
 *       "full width" when its own horizontal span is at least
 *       {@value #FULL_WIDTH_LINE_RATIO} of the page's overall text span — such segments
 *       (titles, running headers/footers) are excluded from column detection and
 *       re-emitted once, spanning the full width, ahead of the columns.</li>
 *   <li>Among the remaining ("narrow") lines, sort by their left edge and sweep for a
 *       single interior gap of at least {@value #MIN_COLUMN_GAP} points with at least
 *       {@value #MIN_LINES_PER_COLUMN} lines on each side. A page with no such gap keeps
 *       today's exact single-pass extraction; a page with more than one such gap (three
 *       or more columns) also falls back rather than guess, per the task's hard-stop
 *       instruction to fail honestly instead of over-generalizing.</li>
 *   <li>When exactly one gap is found, the page is re-emitted as: the full-width header
 *       block (if any), then the left column top-to-bottom, then the right column
 *       top-to-bottom, using {@link PDFTextStripperByArea} so each block keeps its own
 *       natural reading order.</li>
 * </ol>
 *
 * <p>Pages are processed and concatenated in page order, and each page's own columns are
 * emitted left to right. For an ordinary flowing multi-column body (unlike this specific
 * sidebar layout) this also happens to reproduce the correct reading order across a page
 * break: the last column of page N is immediately followed by the first column of page
 * N + 1, exactly like reading a printed two-column article — no special-casing needed.
 *
 * <p>If no page in the document has a detectable column layout, {@link #extractText}
 * returns precisely what a single {@code stripper.getText(document)} call would have
 * returned before this class existed, so every already-correct single-column PDF is
 * unaffected byte for byte.
 */
final class ColumnAwareTextExtractor {

    /**
     * A line narrower than this fraction of the page's overall text width is a column
     * candidate; a line at or above it is treated as a full-width header/footer line and
     * excluded from column detection. Measured against the reference corpus's own
     * two-column page: the wide (abstract) column's own lines run about 0.55-0.60 of
     * the page's overall text width (the column itself is most of the page), while the
     * genuine full-width title/author lines above it run about 0.80-1.0. 0.7 sits
     * squarely in that gap, so a wide-but-still-a-column line is not mistaken for a
     * spanning header line (which would otherwise remove it from column detection
     * entirely and defeat the whole point of this class).
     */
    private static final double FULL_WIDTH_LINE_RATIO = 0.7;

    /**
     * Minimum blank horizontal gap, in PDF points, between two clusters of narrow lines
     * before they are trusted as separate columns. Comfortably above ordinary
     * inter-word/inter-sentence spacing (a few points) and below a typical column
     * gutter (in the reference corpus's two-column layout, the gutter between the
     * affiliations sidebar and the abstract block is about 26pt).
     */
    private static final float MIN_COLUMN_GAP = 12f;

    /**
     * Two lines are treated as the same output row if their top edges are within this
     * many points of each other — comfortably above the sub-point jitter between a
     * superscript and its baseline text, and comfortably below the gap between two
     * consecutive lines at ordinary body line-height.
     */
    private static final float LINE_Y_TOLERANCE = 3f;

    /**
     * A candidate column split is only trusted when each side has at least this many
     * narrow lines, so a single stray short line (a page number, a lone superscript)
     * cannot masquerade as a whole column.
     */
    private static final int MIN_LINES_PER_COLUMN = 2;

    /** Below this header height, in points, there is nothing worth extracting as a separate header block. */
    private static final float MIN_HEADER_HEIGHT = 1f;

    private ColumnAwareTextExtractor() {
    }

    static String extractText(PDDocument document) throws IOException {
        int pageCount = document.getNumberOfPages();
        ColumnLayout[] layouts = new ColumnLayout[pageCount];
        boolean anyColumnar = false;
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            layouts[pageIndex] = detectColumnLayout(document, pageIndex);
            anyColumnar |= layouts[pageIndex] != null;
        }

        if (!anyColumnar) {
            return wholeDocumentText(document);
        }

        StringBuilder text = new StringBuilder();
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            ColumnLayout layout = layouts[pageIndex];
            text.append(layout == null ? singlePageText(document, pageIndex) : columnarPageText(document, pageIndex, layout));
        }
        return text.toString();
    }

    private static String wholeDocumentText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }

    private static String singlePageText(PDDocument document, int pageIndex) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(document);
    }

    private static String columnarPageText(PDDocument document, int pageIndex, ColumnLayout layout) throws IOException {
        PDFTextStripperByArea areaStripper = new PDFTextStripperByArea();
        areaStripper.setSortByPosition(true);

        List<String> regionOrder = new ArrayList<>();
        if (layout.bodyStartY() > MIN_HEADER_HEIGHT) {
            areaStripper.addRegion("header", new Rectangle2D.Float(0, 0, layout.pageWidth(), layout.bodyStartY()));
            regionOrder.add("header");
        }
        float bodyHeight = layout.pageHeight() - layout.bodyStartY();
        areaStripper.addRegion("left", new Rectangle2D.Float(0, layout.bodyStartY(), layout.columnBoundaryX(), bodyHeight));
        areaStripper.addRegion(
                "right",
                new Rectangle2D.Float(layout.columnBoundaryX(), layout.bodyStartY(), layout.pageWidth() - layout.columnBoundaryX(), bodyHeight));
        regionOrder.add("left");
        regionOrder.add("right");

        areaStripper.extractRegions(document.getPage(pageIndex));

        StringBuilder pageText = new StringBuilder();
        for (String region : regionOrder) {
            pageText.append(areaStripper.getTextForRegion(region));
        }
        return pageText.toString();
    }

    private static ColumnLayout detectColumnLayout(PDDocument document, int pageIndex) throws IOException {
        List<Glyph> glyphs = collectGlyphs(document, pageIndex);
        if (glyphs.isEmpty()) {
            return null;
        }

        float globalMinX = Float.MAX_VALUE;
        float globalMaxX = -Float.MAX_VALUE;
        for (Glyph glyph : glyphs) {
            globalMinX = Math.min(globalMinX, glyph.xStart());
            globalMaxX = Math.max(globalMaxX, glyph.xEnd());
        }
        float textWidth = globalMaxX - globalMinX;
        if (textWidth <= 0) {
            return null;
        }

        List<LineExtent> narrowLines = groupLines(glyphs).stream()
                .filter(line -> (line.xEnd() - line.xStart()) < FULL_WIDTH_LINE_RATIO * textWidth)
                .sorted(Comparator.comparing(LineExtent::xStart))
                .toList();
        if (narrowLines.size() < 2 * MIN_LINES_PER_COLUMN) {
            return null;
        }

        int splitIndex = -1;
        float clusterMaxXEnd = narrowLines.get(0).xEnd();
        for (int i = 1; i < narrowLines.size(); i++) {
            LineExtent line = narrowLines.get(i);
            if (line.xStart() - clusterMaxXEnd >= MIN_COLUMN_GAP) {
                if (splitIndex != -1) {
                    // A second gap means three-or-more columns: unsupported, fall back
                    // rather than guess (hard-stop instruction: fail honestly).
                    return null;
                }
                splitIndex = i;
            }
            clusterMaxXEnd = Math.max(clusterMaxXEnd, line.xEnd());
        }
        if (splitIndex == -1) {
            return null;
        }

        List<LineExtent> leftCluster = narrowLines.subList(0, splitIndex);
        List<LineExtent> rightCluster = narrowLines.subList(splitIndex, narrowLines.size());
        if (leftCluster.size() < MIN_LINES_PER_COLUMN || rightCluster.size() < MIN_LINES_PER_COLUMN) {
            return null;
        }

        float leftMaxXEnd = -Float.MAX_VALUE;
        for (LineExtent line : leftCluster) {
            leftMaxXEnd = Math.max(leftMaxXEnd, line.xEnd());
        }
        float rightMinXStart = Float.MAX_VALUE;
        for (LineExtent line : rightCluster) {
            rightMinXStart = Math.min(rightMinXStart, line.xStart());
        }
        float columnBoundaryX = (leftMaxXEnd + rightMinXStart) / 2f;

        float bodyStartY = Float.MAX_VALUE;
        for (LineExtent line : narrowLines) {
            bodyStartY = Math.min(bodyStartY, line.y());
        }

        PDPage page = document.getPage(pageIndex);
        PDRectangle box = page.getMediaBox();
        float bodyHeight = box.getHeight() - bodyStartY;
        if (bodyHeight <= 0) {
            return null;
        }
        return new ColumnLayout(bodyStartY, columnBoundaryX, box.getWidth(), box.getHeight());
    }

    private static List<Glyph> collectGlyphs(PDDocument document, int pageIndex) throws IOException {
        List<Glyph> glyphs = new ArrayList<>();
        PDFTextStripper collector = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                super.processTextPosition(text);
                String unicode = text.getUnicode();
                if (unicode != null && !unicode.isBlank()) {
                    glyphs.add(new Glyph(text.getXDirAdj(), text.getXDirAdj() + text.getWidth(), text.getYDirAdj()));
                }
            }
        };
        collector.setSortByPosition(true);
        collector.setStartPage(pageIndex + 1);
        collector.setEndPage(pageIndex + 1);
        collector.getText(document);
        return glyphs;
    }

    /**
     * Groups glyphs into rows by y-proximity only, then splits each row into one or
     * more horizontal segments wherever two consecutive glyphs (by x) are at least
     * {@value #MIN_COLUMN_GAP} points apart. The second step is what makes this
     * column-aware in the first place: a sidebar and a wide text block that happen to
     * share the same row (the exact case this class exists for) would otherwise be
     * merged into a single, spuriously "full width" line by y-proximity grouping alone,
     * hiding the column gap that a purely per-row extent would have exposed.
     */
    private static List<LineExtent> groupLines(List<Glyph> glyphs) {
        List<Glyph> sortedByY = glyphs.stream().sorted(Comparator.comparing(Glyph::y)).toList();

        List<List<Glyph>> rows = new ArrayList<>();
        List<Glyph> currentRow = new ArrayList<>();
        float rowY = sortedByY.get(0).y();
        for (Glyph glyph : sortedByY) {
            if (!currentRow.isEmpty() && glyph.y() - rowY > LINE_Y_TOLERANCE) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                rowY = glyph.y();
            }
            currentRow.add(glyph);
        }
        rows.add(currentRow);

        List<LineExtent> lines = new ArrayList<>();
        for (List<Glyph> row : rows) {
            lines.addAll(splitRowIntoSegments(row));
        }
        return lines;
    }

    private static List<LineExtent> splitRowIntoSegments(List<Glyph> row) {
        float rowTopY = Float.MAX_VALUE;
        for (Glyph glyph : row) {
            rowTopY = Math.min(rowTopY, glyph.y());
        }

        List<Glyph> sortedByX = row.stream().sorted(Comparator.comparing(Glyph::xStart)).toList();
        List<LineExtent> segments = new ArrayList<>();
        float segmentMinX = sortedByX.get(0).xStart();
        float segmentMaxX = sortedByX.get(0).xEnd();
        for (int i = 1; i < sortedByX.size(); i++) {
            Glyph glyph = sortedByX.get(i);
            if (glyph.xStart() - segmentMaxX >= MIN_COLUMN_GAP) {
                segments.add(new LineExtent(segmentMinX, segmentMaxX, rowTopY));
                segmentMinX = glyph.xStart();
                segmentMaxX = glyph.xEnd();
            } else {
                segmentMaxX = Math.max(segmentMaxX, glyph.xEnd());
            }
        }
        segments.add(new LineExtent(segmentMinX, segmentMaxX, rowTopY));
        return segments;
    }

    private record Glyph(float xStart, float xEnd, float y) {
    }

    private record LineExtent(float xStart, float xEnd, float y) {
    }

    private record ColumnLayout(float bodyStartY, float columnBoundaryX, float pageWidth, float pageHeight) {
    }
}
