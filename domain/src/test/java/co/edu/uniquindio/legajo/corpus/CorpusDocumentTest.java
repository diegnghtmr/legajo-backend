package co.edu.uniquindio.legajo.corpus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural invariants only: {@link CorpusDocument} must not accept a null field and
 * must own an immutable copy of {@code authors}. Business validity (non-blank title,
 * hash match, etc.) is {@link CorpusVerifier}'s responsibility, not the record's, so
 * a corpus with those problems can still be loaded and reported on.
 */
class CorpusDocumentTest {

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CorpusDocument(null, "t", List.of("a"), "abs", "src", "GROBID", true, "sha"));
        assertThatNullPointerException()
                .isThrownBy(() -> new CorpusDocument("id", null, List.of("a"), "abs", "src", "GROBID", true, "sha"));
        assertThatNullPointerException()
                .isThrownBy(() -> new CorpusDocument("id", "t", null, "abs", "src", "GROBID", true, "sha"));
    }

    @Test
    void authorsListIsImmutable() {
        List<String> mutableAuthors = new ArrayList<>(List.of("Ada Lovelace"));
        CorpusDocument document = new CorpusDocument("id", "t", mutableAuthors, "abs", "src", "GROBID", true, "sha");

        mutableAuthors.add("Grace Hopper");

        assertThat(document.authors()).containsExactly("Ada Lovelace");
        assertThrows(UnsupportedOperationException.class, () -> document.authors().add("Katherine Johnson"));
    }

    @Test
    void toleratesBlankBusinessFieldsBecauseVerificationIsSeparate() {
        CorpusDocument blank = new CorpusDocument("", "", List.of(), "", "", "", false, "");

        assertThat(blank.title()).isEmpty();
        assertThat(blank.authors()).isEmpty();
    }
}
