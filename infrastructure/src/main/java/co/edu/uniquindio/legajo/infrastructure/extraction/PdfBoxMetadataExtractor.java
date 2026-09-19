package co.edu.uniquindio.legajo.infrastructure.extraction;

import co.edu.uniquindio.legajo.infrastructure.ingest.IngestionTextCleaner;
import co.edu.uniquindio.legajo.port.ExtractedPdfMetadata;
import co.edu.uniquindio.legajo.port.PdfExtractionException;
import co.edu.uniquindio.legajo.port.PdfMetadataExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reserve PDF metadata extractor (TRD §6.1, item 2; TRD §8): {@code Loader.loadPDF} +
 * {@link ColumnAwareTextExtractor} for left-to-right, top-to-bottom line order (T4c:
 * column-aware for a page whose body is laid out in side-by-side blocks, identical to
 * plain {@code PDFTextStripper} otherwise), plus line-based heuristics for the abstract,
 * title and authors. Title and authors are "best effort" by design (TRD §6.1, item 4
 * only requires the fields to be persisted, not to be exact); the abstract heuristic is
 * the one that matters, because {@link FallbackPdfMetadataExtractor} and {@code
 * verify-corpus}'s non-blank-abstract rule both depend on it finding real text.
 */
public final class PdfBoxMetadataExtractor implements PdfMetadataExtractor {

    static final String EXTRACTED_BY = "PDFBox";

    private static final Pattern ABSTRACT_HEADING = Pattern.compile("(?i)^abstract\\s*[:.\\-]?\\s*(.*)$");
    private static final Pattern NEXT_SECTION_HEADING = Pattern.compile(
            "(?i)^(keywords|index terms|introduction|1\\.?\\s*introduction|i\\.?\\s*introduction|1\\.)\\b.*$");
    private static final Pattern AUTHOR_SEPARATOR = Pattern.compile(",|\\band\\b|&");

    /**
     * Some journals render a section heading in a letter-spaced display style (a single
     * space between every letter, purely typographic — "K E Y W O R D S" instead of
     * "Keywords") that PDFBox reproduces literally. Matches a whole line made of four
     * or more single letters each separated by exactly one space, generic enough for
     * any such heading, not just "Keywords": four letters is comfortably above a real
     * two-letter/three-letter initialism ("Dr. A B", an author's middle initials) that
     * should not be collapsed.
     */
    private static final Pattern LETTER_SPACED_WORD = Pattern.compile("^(?:\\p{L}\\s){3,}\\p{L}$");

    @Override
    public ExtractedPdfMetadata extract(Path pdfPath) {
        Objects.requireNonNull(pdfPath, "pdfPath");
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            String rawText = ColumnAwareTextExtractor.extractText(document);

            List<String> nonBlankLines = rawText.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
            String title = nonBlankLines.isEmpty() ? "" : nonBlankLines.get(0);
            List<String> authors = nonBlankLines.size() < 2 ? List.of() : extractAuthors(nonBlankLines.get(1));
            String abstractText = extractAbstract(rawText.lines().toList());

            return new ExtractedPdfMetadata(
                    IngestionTextCleaner.clean(title),
                    authors,
                    IngestionTextCleaner.clean(abstractText),
                    EXTRACTED_BY);
        } catch (IOException e) {
            throw new PdfExtractionException("PDFBox extraction failed for " + pdfPath, e);
        }
    }

    private static List<String> extractAuthors(String authorLine) {
        return AUTHOR_SEPARATOR.splitAsStream(authorLine)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .toList();
    }

    private static String extractAbstract(List<String> rawLines) {
        int headingIndex = -1;
        String firstLineRemainder = "";
        for (int i = 0; i < rawLines.size(); i++) {
            Matcher matcher = ABSTRACT_HEADING.matcher(rawLines.get(i).trim());
            if (matcher.matches()) {
                headingIndex = i;
                firstLineRemainder = matcher.group(1);
                break;
            }
        }
        if (headingIndex == -1) {
            return "";
        }

        StringBuilder abstractBuilder = new StringBuilder();
        if (!firstLineRemainder.isBlank()) {
            abstractBuilder.append(firstLineRemainder).append('\n');
        }
        for (int i = headingIndex + 1; i < rawLines.size(); i++) {
            String trimmed = rawLines.get(i).trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (NEXT_SECTION_HEADING.matcher(collapseLetterSpacing(trimmed)).matches()) {
                break;
            }
            abstractBuilder.append(trimmed).append('\n');
        }
        return abstractBuilder.toString();
    }

    private static String collapseLetterSpacing(String line) {
        return LETTER_SPACED_WORD.matcher(line).matches() ? line.replace(" ", "") : line;
    }
}
