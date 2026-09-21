package co.edu.uniquindio.legajo.rest.testsupport;

import co.edu.uniquindio.legajo.infrastructure.embedding.EmbeddingApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * Test-only fixture, never packaged: it lives under {@code src/test/java}, so it is on the
 * classpath only while tests run and is picked up by {@code @SpringBootTest}'s component
 * scan (rooted at {@code LegajoApplication}'s base package, which this class's package is
 * under) the same way any production {@code @RestController} would be.
 *
 * <p>Its first two endpoints simulate a server-side bug: code that throws a raw
 * {@link IllegalArgumentException} or {@link NoSuchElementException} for a reason that has
 * nothing to do with request validation or a missing resource — the exact defect
 * {@code ProblemDetailExceptionHandler} used to mis-report as a 400 or 404 (feature doc
 * {@code rest-api.md}, task A3b, advisory {@code R3-broad-exception-mapping}). A real request
 * through the real handler is the only way to prove the fix without guessing at Spring's
 * exception-resolution behavior.
 *
 * <p>The third endpoint simulates NFR-QA-12's live-embedding degradation: no real request
 * path throws {@link EmbeddingApiException} today (see feature doc task A4's finding on
 * {@code ProblemDetailExceptionHandler}), so this fixture is the only way to prove the 503
 * mapping through a real HTTP request rather than a bare unit test of the handler method.
 */
@RestController
@RequestMapping("/api/v1/test-only")
class BuggyTestOnlyController {

    @GetMapping("/raw-illegal-argument")
    String rawIllegalArgument() {
        throw new IllegalArgumentException("simulated server-side bug, unrelated to request validation");
    }

    @GetMapping("/raw-no-such-element")
    String rawNoSuchElement() {
        throw new NoSuchElementException("simulated server-side bug, unrelated to a missing resource");
    }

    @GetMapping("/embedding-api-failure")
    String embeddingApiFailure() {
        throw new EmbeddingApiException("simulated live embedding API failure (NFR-QA-12)");
    }
}
