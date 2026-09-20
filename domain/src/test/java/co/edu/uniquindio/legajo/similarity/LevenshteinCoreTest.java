package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Levenshtein DP core is generic over {@code List<T>} (TRD §13): this is the
 * mandatory character-level test of that generic core (list elements are
 * {@link Character}), kept distinct from the token-level golden tests of
 * {@link LevenshteinTest}, which exercise the {@code levenshtein} capability itself.
 */
class LevenshteinCoreTest {

    @Test
    void kittenToSittingDistanceIsThree() {
        int distance = LevenshteinCore.distance(charList("kitten"), charList("sitting"));

        assertThat(distance).isEqualTo(3);
    }

    @Test
    void distanceBetweenIdenticalSequencesIsZero() {
        int distance = LevenshteinCore.distance(charList("abc"), charList("abc"));

        assertThat(distance).isZero();
    }

    @Test
    void distanceBetweenTwoEmptySequencesIsZero() {
        int distance = LevenshteinCore.distance(List.of(), List.of());

        assertThat(distance).isZero();
    }

    private static List<Character> charList(String text) {
        return text.chars().mapToObj(c -> (char) c).toList();
    }
}
