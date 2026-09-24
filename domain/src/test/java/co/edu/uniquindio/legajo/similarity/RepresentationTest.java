package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The three vector-space representations the clustering distance base can be built over:
 * {@code tfidf-cosine} (default), {@code embedding-local}, {@code embedding-api}.
 */
class RepresentationTest {

    @Test
    void idsMatchTheThreeFixedValues() {
        assertThat(Representation.TFIDF_COSINE.id()).isEqualTo("tfidf-cosine");
        assertThat(Representation.EMBEDDING_LOCAL.id()).isEqualTo("embedding-local");
        assertThat(Representation.EMBEDDING_API.id()).isEqualTo("embedding-api");
    }

    @Test
    void thereAreExactlyTheThreeFixedRepresentations() {
        assertThat(Representation.values()).containsExactly(
                Representation.TFIDF_COSINE, Representation.EMBEDDING_LOCAL, Representation.EMBEDDING_API);
    }

    @Test
    void defaultRepresentationIsTfidfCosine() {
        // The default and documented run is tfidf-cosine.
        assertThat(Representation.DEFAULT).isEqualTo(Representation.TFIDF_COSINE);
    }

    @Test
    void fromIdRoundTripsEveryDeclaredId() {
        for (Representation representation : Representation.values()) {
            assertThat(Representation.fromId(representation.id())).isEqualTo(representation);
        }
    }

    @Test
    void fromIdRejectsAnUnknownId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Representation.fromId("cosine-similarity"))
                .withMessageContaining("cosine-similarity");
    }

    @Test
    void fromIdRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Representation.fromId(null));
    }
}
