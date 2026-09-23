package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the {@code data/corpus.json} path resolver the real-corpus (SLO) benchmarks
 * and their input builders share. Written before {@link CorpusPaths} exists (odd/tasks/
 * jmh-benchmarks.md, task J1: strict TDD).
 */
class CorpusPathsTest {

    @Test
    void resolvesAnExistingCorpusJsonFile() {
        Path resolved = CorpusPaths.resolveCorpusJson();

        assertThat(Files.isRegularFile(resolved)).isTrue();
        assertThat(resolved.toString().replace('\\', '/')).endsWith("data/corpus.json");
    }
}
