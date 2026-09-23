package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for the {@code data/corpus.json} path resolver the real-corpus (SLO) benchmarks
 * and their input builders share. Written before {@link CorpusPaths} exists (odd/tasks/
 * jmh-benchmarks.md, task J1: strict TDD).
 */
class CorpusPathsTest {

    private static final String OVERRIDE_PROPERTY = "legajo.benchmarks.corpusPath";

    @AfterEach
    void clearOverrideProperty() {
        System.clearProperty(OVERRIDE_PROPERTY);
    }

    @Test
    void resolvesAnExistingCorpusJsonFile() {
        Path resolved = CorpusPaths.resolveCorpusJson();

        assertThat(Files.isRegularFile(resolved)).isTrue();
        assertThat(resolved.toString().replace('\\', '/')).endsWith("data/corpus.json");
    }

    @Test
    void overrideThatExistsIsResolvedAsIs(@TempDir Path tempDir) throws IOException {
        Path overrideFile = tempDir.resolve("override-corpus.json");
        Files.writeString(overrideFile, "{}");
        System.setProperty(OVERRIDE_PROPERTY, overrideFile.toString());

        Path resolved = CorpusPaths.resolveCorpusJson();

        assertThat(resolved).isEqualTo(overrideFile);
    }

    @Test
    void overrideThatDoesNotExistFailsWithAClearMessage(@TempDir Path tempDir) {
        Path missingOverride = tempDir.resolve("does-not-exist-corpus.json");
        System.setProperty(OVERRIDE_PROPERTY, missingOverride.toString());

        assertThatThrownBy(CorpusPaths::resolveCorpusJson)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OVERRIDE_PROPERTY)
                .hasMessageContaining(missingOverride.toString())
                .hasMessageContaining("regular file");
    }

    @Test
    void overrideThatIsADirectoryFailsWithAClearMessage(@TempDir Path tempDir) {
        System.setProperty(OVERRIDE_PROPERTY, tempDir.toString());

        assertThatThrownBy(CorpusPaths::resolveCorpusJson)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OVERRIDE_PROPERTY)
                .hasMessageContaining(tempDir.toString())
                .hasMessageContaining("regular file");
    }

    @Test
    void notFoundByWalkFailsNamingTheStartingDirectoryAndTheOverrideOption(@TempDir Path tempDir) {
        assertThatThrownBy(() -> CorpusPaths.resolveCorpusJson(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data/corpus.json")
                .hasMessageContaining(tempDir.toString())
                .hasMessageContaining("-D" + OVERRIDE_PROPERTY);
    }
}
