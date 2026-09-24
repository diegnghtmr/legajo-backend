package co.edu.uniquindio.legajo.benchmarks.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for the {@code data/corpus.json} path resolver the real-corpus (SLO) benchmarks
 * and their input builders share. Written before {@link CorpusPaths} exists (strict TDD).
 */
class CorpusPathsTest {

    /** Null means the property was absent before the test, not set to the string {@code "null"}. */
    private String overridePropertyBeforeTest;

    /**
     * Remembers whatever this JVM's {@code CorpusPaths.OVERRIDE_PROPERTY} held before the test
     * (for {@link #restoreOverrideProperty} to put back afterward), then clears it. Clearing it
     * here, not just remembering it, is what isolates {@link
     * #notFoundByWalkFailsNamingTheStartingDirectoryAndTheOverrideOption}: without this, a
     * leftover value from a real {@code -D} invocation this test JVM was started with (or from
     * another test in the same JVM whose own {@code @AfterEach} has not run yet) would make that
     * walk test silently take the override branch instead of exercising the walk-up-and-fail
     * path it is named for.
     */
    @BeforeEach
    void rememberAndClearOverrideProperty() {
        overridePropertyBeforeTest = System.getProperty(CorpusPaths.OVERRIDE_PROPERTY);
        System.clearProperty(CorpusPaths.OVERRIDE_PROPERTY);
    }

    /**
     * Restores whatever this JVM's {@code CorpusPaths.OVERRIDE_PROPERTY} held before the test,
     * instead of unconditionally clearing it: a plain {@code clearProperty} would erase a value
     * a real {@code -D} invocation had set for the whole JVM, beyond this test class.
     */
    @AfterEach
    void restoreOverrideProperty() {
        if (overridePropertyBeforeTest == null) {
            System.clearProperty(CorpusPaths.OVERRIDE_PROPERTY);
        } else {
            System.setProperty(CorpusPaths.OVERRIDE_PROPERTY, overridePropertyBeforeTest);
        }
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
        System.setProperty(CorpusPaths.OVERRIDE_PROPERTY, overrideFile.toString());

        Path resolved = CorpusPaths.resolveCorpusJson();

        assertThat(resolved).isEqualTo(overrideFile);
    }

    @Test
    void overrideThatDoesNotExistFailsWithAClearMessage(@TempDir Path tempDir) {
        Path missingOverride = tempDir.resolve("does-not-exist-corpus.json");
        System.setProperty(CorpusPaths.OVERRIDE_PROPERTY, missingOverride.toString());

        assertThatThrownBy(CorpusPaths::resolveCorpusJson)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CorpusPaths.OVERRIDE_PROPERTY)
                .hasMessageContaining(missingOverride.toString())
                .hasMessageContaining("regular file");
    }

    @Test
    void overrideThatIsADirectoryFailsWithAClearMessage(@TempDir Path tempDir) {
        System.setProperty(CorpusPaths.OVERRIDE_PROPERTY, tempDir.toString());

        assertThatThrownBy(CorpusPaths::resolveCorpusJson)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CorpusPaths.OVERRIDE_PROPERTY)
                .hasMessageContaining(tempDir.toString())
                .hasMessageContaining("regular file");
    }

    /**
     * The override property is guaranteed clear here by {@link #rememberAndClearOverrideProperty}
     * (not merely assumed absent), so this test actually exercises the walk-up-and-fail path
     * even when this test JVM itself was started with a real {@code -D} override, or another test
     * in this class ran first and left a value behind for its own {@code @AfterEach} to restore.
     */
    @Test
    void notFoundByWalkFailsNamingTheStartingDirectoryAndTheOverrideOption(@TempDir Path tempDir) {
        assertThatThrownBy(() -> CorpusPaths.resolveCorpusJson(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data/corpus.json")
                .hasMessageContaining(tempDir.toString())
                .hasMessageContaining("-D" + CorpusPaths.OVERRIDE_PROPERTY);
    }
}
