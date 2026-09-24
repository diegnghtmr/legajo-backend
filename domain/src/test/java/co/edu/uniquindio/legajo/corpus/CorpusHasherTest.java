package co.edu.uniquindio.legajo.corpus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden sha256 values computed independently with the shell, not with Java, to avoid
 * validating the implementation against itself:
 *
 * <pre>
 * printf '%s' "Test abstract one." | sha256sum
 *   -&gt; 7c1882e9e6828c3625240ed66bf6c152b5389c83b305cb68766020920e5fc8e3
 * printf '%s' "Another abstract." | sha256sum
 *   -&gt; bb6570dac72c386a7c144e50bd0049d9da9682e48563575dbc94578e18723d6f
 * printf '%s' "" | sha256sum
 *   -&gt; e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
 * printf 'd01:%s\nd02:%s\n' \
 *   7c1882e9e6828c3625240ed66bf6c152b5389c83b305cb68766020920e5fc8e3 \
 *   bb6570dac72c386a7c144e50bd0049d9da9682e48563575dbc94578e18723d6f \
 *   | sha256sum
 *   -&gt; 1311812eb3bdb0901129894bee5bd0b70383c317f600e890bbb1aa5be05b00a0
 * </pre>
 *
 * <p>Note: the printf must be piped directly into sha256sum. Capturing it first with
 * {@code $(...)} command substitution strips the trailing newline that is
 * required after the last document and silently produces a different hash.
 */
class CorpusHasherTest {

    private static final String ABSTRACT_1 = "Test abstract one.";
    private static final String ABSTRACT_2 = "Another abstract.";
    private static final String ABSTRACT_1_SHA = "7c1882e9e6828c3625240ed66bf6c152b5389c83b305cb68766020920e5fc8e3";
    private static final String ABSTRACT_2_SHA = "bb6570dac72c386a7c144e50bd0049d9da9682e48563575dbc94578e18723d6f";
    private static final String EMPTY_SHA = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String CORPUS_SHA = "1311812eb3bdb0901129894bee5bd0b70383c317f600e890bbb1aa5be05b00a0";

    @Test
    void abstractSha256MatchesIndependentlyComputedShellHash() {
        assertThat(CorpusHasher.abstractSha256(ABSTRACT_1)).isEqualTo(ABSTRACT_1_SHA);
        assertThat(CorpusHasher.abstractSha256(ABSTRACT_2)).isEqualTo(ABSTRACT_2_SHA);
    }

    @Test
    void abstractSha256OfEmptyStringMatchesKnownValue() {
        assertThat(CorpusHasher.abstractSha256("")).isEqualTo(EMPTY_SHA);
    }

    @Test
    void abstractSha256IsLowercaseHex() {
        assertThat(CorpusHasher.abstractSha256(ABSTRACT_1)).matches("[0-9a-f]{64}");
    }

    @Test
    void corpusSha256MatchesIndependentlyComputedShellHashForDocumentsInAscendingIdOrder() {
        CorpusDocument doc1 = documentWith("d01", ABSTRACT_1_SHA);
        CorpusDocument doc2 = documentWith("d02", ABSTRACT_2_SHA);

        String corpusSha = CorpusHasher.corpusSha256(List.of(doc1, doc2));

        assertThat(corpusSha).isEqualTo(CORPUS_SHA);
    }

    @Test
    void corpusSha256SortsDocumentsByIdRegardlessOfInputOrder() {
        CorpusDocument doc1 = documentWith("d01", ABSTRACT_1_SHA);
        CorpusDocument doc2 = documentWith("d02", ABSTRACT_2_SHA);

        String ascendingInput = CorpusHasher.corpusSha256(List.of(doc1, doc2));
        String descendingInput = CorpusHasher.corpusSha256(List.of(doc2, doc1));

        assertThat(descendingInput).isEqualTo(ascendingInput).isEqualTo(CORPUS_SHA);
    }

    private static CorpusDocument documentWith(String id, String abstractSha256) {
        return new CorpusDocument(id, "Title", List.of("Author"), "abstract text",
                "data/pdfs/01.pdf", "GROBID", true, abstractSha256);
    }
}
