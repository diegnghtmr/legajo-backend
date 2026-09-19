package co.edu.uniquindio.legajo.port;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural invariants only, mirroring {@code CorpusDocumentTest}: no null field, an
 * immutable {@code authors} list, and a blank abstract is a tolerated, non-exceptional
 * value that {@link ExtractedPdfMetadata#hasBlankAbstract()} exposes for the fallback
 * chain to act on.
 */
class ExtractedPdfMetadataTest {

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ExtractedPdfMetadata(null, List.of("a"), "abs", "GROBID"));
        assertThatNullPointerException()
                .isThrownBy(() -> new ExtractedPdfMetadata("t", null, "abs", "GROBID"));
        assertThatNullPointerException()
                .isThrownBy(() -> new ExtractedPdfMetadata("t", List.of("a"), null, "GROBID"));
        assertThatNullPointerException()
                .isThrownBy(() -> new ExtractedPdfMetadata("t", List.of("a"), "abs", null));
    }

    @Test
    void authorsListIsImmutable() {
        List<String> mutableAuthors = new ArrayList<>(List.of("Ada Lovelace"));
        ExtractedPdfMetadata metadata = new ExtractedPdfMetadata("t", mutableAuthors, "abs", "GROBID");

        mutableAuthors.add("Grace Hopper");

        assertThat(metadata.authors()).containsExactly("Ada Lovelace");
        assertThrows(UnsupportedOperationException.class, () -> metadata.authors().add("Katherine Johnson"));
    }

    @Test
    void hasBlankAbstractIsTrueForBlankOrEmptyText() {
        assertThat(new ExtractedPdfMetadata("t", List.of(), "", "GROBID").hasBlankAbstract()).isTrue();
        assertThat(new ExtractedPdfMetadata("t", List.of(), "   ", "GROBID").hasBlankAbstract()).isTrue();
        assertThat(new ExtractedPdfMetadata("t", List.of(), "content", "GROBID").hasBlankAbstract()).isFalse();
    }
}
