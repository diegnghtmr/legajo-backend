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
 * any one document (TC-01, reusability over any PDF folder). An earlier version of this
 * class tried to classify each line as "narrow" or "full width" by comparing its own
 * width against a fraction of the page's overall text width, but that measure is fooled
 * by an ordinary wrapped title: a short wrapped header line ("...critical review") is
 * individually just as narrow as a real column line, yet it still spans the same x-range
 * a header occupies, so it silently bridged the very gap the detector was looking for.
 * The signal this class actually uses is more direct and does not have that failure
 * mode:
 *
 * <ol>
 *   <li>Collect every glyph's horizontal extent and top-adjusted y position on the page
 *       (via {@link TextPosition}, ignoring page rotation quirks by using the
 *       "direction-adjusted" accessors PDFBox already provides for this purpose).</li>
 *   <li>Group glyphs into rows by y-proximity, then split each row into segments
 *       wherever an internal x-gap is at least {@value #MIN_COLUMN_GAP} points wide —
 *       comfortably above ordinary word/sentence spacing and below a real column
 *       gutter. A row that splits into exactly two segments is direct, row-level
 *       evidence of two side-by-side blocks at that height: no width ratio needed,
 *       because nothing else about that row matters once two genuinely separate blocks
 *       have been found sharing it. A row with one segment (an ordinary line, whether
 *       wide or narrow) or three-or-more segments (unsupported multi-column punctuation
 *       like a piped author line, or 3+ real columns) contributes no evidence either
 *       way and is simply not counted.</li>
 *   <li>If at least {@value #MIN_LINES_PER_COLUMN} such two-segment rows are found, the
 *       column boundary is the midpoint between the widest reach of every row's left
 *       segment and the narrowest reach of every row's right segment, and the page's
 *       column body starts at the topmost such row. Fewer than that many rows keeps
 *       today's exact single-pass extraction, per the task's hard-stop instruction to
 *       fail honestly rather than trust a single fluke row (a stray page number next to
 *       a footnote, for instance).</li>
 *   <li>Everything above that topmost row is re-emitted once, spanning the full page
 *       width, ahead of the two columns, using {@link PDFTextStripperByArea} so each
 *       block keeps its own natural reading order.</li>
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
     * A candidate column split is only trusted once at least this many rows split into
     * exactly two segments, so a single stray two-segment row (a page number next to a
     * footnote, for instance) cannot masquerade as a whole two-column layout.
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

        List<TwoSegmentRow> pairs = groupIntoRows(glyphs).stream()
                .map(ColumnAwareTextExtractor::splitRowIntoSegments)
                .filter(segments -> segments.size() == 2)
                .map(segments -> new TwoSegmentRow(segments.get(0), segments.get(1)))
                .toList();
        if (pairs.size() < MIN_LINES_PER_COLUMN) {
            return null;
        }

        float leftMaxXEnd = -Float.MAX_VALUE;
        float rightMinXStart = Float.MAX_VALUE;
        float bodyStartY = Float.MAX_VALUE;
        for (TwoSegmentRow pair : pairs) {
            leftMaxXEnd = Math.max(leftMaxXEnd, pair.left().xEnd());
            rightMinXStart = Math.min(rightMinXStart, pair.right().xStart());
            bodyStartY = Math.min(bodyStartY, pair.left().y());
        }
        if (rightMinXStart - leftMaxXEnd < MIN_COLUMN_GAP) {
            // Defensive: should not happen given each pair's own segments were split at
            // this same threshold, but a stray row spanning an unusual x-range must not
            // silently collapse the two columns into an overlapping boundary.
            return null;
        }
        float columnBoundaryX = (leftMaxXEnd + rightMinXStart) / 2f;

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

    /** Groups glyphs into rows by y-proximity only (no x involved yet). */
    private static List<List<Glyph>> groupIntoRows(List<Glyph> glyphs) {
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
        return rows;
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

    /** A row that split into exactly two segments: direct evidence of two side-by-side blocks at that height. */
    private record TwoSegmentRow(LineExtent left, LineExtent right) {
    }

    private record ColumnLayout(float bodyStartY, float columnBoundaryX, float pageWidth, float pageHeight) {
    }
}
