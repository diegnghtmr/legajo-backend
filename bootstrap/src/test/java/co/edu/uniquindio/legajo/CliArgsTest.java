package co.edu.uniquindio.legajo;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Argument parsing shared by the ingestion CLI entry points. */
class CliArgsTest {

    @Test
    void parsesKeyValuePairs() {
        Map<String, String> options = CliArgs.parse(new String[] {"--input=data/pdfs", "--output=data/corpus.json"});

        assertThat(options).containsEntry("input", "data/pdfs").containsEntry("output", "data/corpus.json");
    }

    @Test
    void parsesAStandaloneFlagAsTrue() {
        Map<String, String> options = CliArgs.parse(new String[] {"--all"});

        assertThat(options).containsEntry("all", "true");
    }

    @Test
    void ignoresArgumentsWithoutADoubleDashPrefix() {
        Map<String, String> options = CliArgs.parse(new String[] {"positional", "--ids=d01,d02"});

        assertThat(options).containsOnlyKeys("ids");
    }

    @Test
    void returnsAnEmptyMapForNoArguments() {
        assertThat(CliArgs.parse(new String[0])).isEmpty();
    }
}
