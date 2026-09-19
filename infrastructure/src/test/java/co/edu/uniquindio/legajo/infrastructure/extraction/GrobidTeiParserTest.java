package co.edu.uniquindio.legajo.infrastructure.extraction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses a GROBID {@code processHeaderDocument} TEI response (title, authors as
 * forename+surname, abstract paragraphs) using the fixture files under
 * {@code src/test/resources/extraction}, independent of any HTTP call.
 */
class GrobidTeiParserTest {

    @Test
    void parsesTitleAuthorsAndAbstractFromTei() throws IOException {
        String tei = readFixture("extraction/sample-header.tei.xml");

        GrobidTeiParser.TeiExtraction extraction = GrobidTeiParser.parse(tei);

        assertThat(extraction.title()).isEqualTo("A Study of Information Retrieval Methods");
        assertThat(extraction.authors()).containsExactly("Ada Lovelace", "Grace Hopper");
        assertThat(extraction.abstractText())
                .contains("This paper studies several information retrieval methods")
                .contains("Results show consistent improvements across all methods.");
    }

    @Test
    void returnsABlankAbstractWhenTheTeiHasNoAbstractParagraphs() throws IOException {
        String tei = readFixture("extraction/empty-abstract-header.tei.xml");

        GrobidTeiParser.TeiExtraction extraction = GrobidTeiParser.parse(tei);

        assertThat(extraction.title()).isEqualTo("A Paper Without An Extracted Abstract");
        assertThat(extraction.authors()).containsExactly("John Doe");
        assertThat(extraction.abstractText()).isBlank();
    }

    private static String readFixture(String resourcePath) throws IOException {
        try (InputStream in = GrobidTeiParserTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            Objects.requireNonNull(in, "fixture not found: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
