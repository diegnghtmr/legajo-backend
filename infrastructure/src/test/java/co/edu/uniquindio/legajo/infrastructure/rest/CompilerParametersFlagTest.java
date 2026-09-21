package co.edu.uniquindio.legajo.infrastructure.rest;

import co.edu.uniquindio.legajo.infrastructure.rest.corpus.CorpusController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the root build's {@code -parameters} javac flag (feature doc {@code rest-api.md},
 * task A3b) is actually effective on this module's compiled classes, not merely declared in
 * {@code build.gradle.kts}. Without it, {@link Parameter#isNamePresent()} is {@code false}
 * for every compiled method parameter, so an unnamed {@code @PathVariable}/{@code
 * @RequestParam} throws {@link IllegalArgumentException} at request time instead of at
 * compile time — task A3 worked around the gap by naming every parameter explicitly on its
 * annotations, which hides the underlying compiler gap for the next controller that forgets
 * to.
 *
 * <p>{@link CorpusController#get(String)} is a real, already-shipped controller method
 * (task A3), so this asserts the flag against production bytecode rather than a
 * purpose-built fixture.
 */
class CompilerParametersFlagTest {

    @Test
    void controllerMethodParameterNameSurvivesCompilation() throws NoSuchMethodException {
        Method get = CorpusController.class.getMethod("get", String.class);
        Parameter parameter = get.getParameters()[0];

        assertThat(parameter.isNamePresent())
                .as("javac must be invoked with -parameters for this to be true")
                .isTrue();
        assertThat(parameter.getName()).isEqualTo("id");
    }
}
