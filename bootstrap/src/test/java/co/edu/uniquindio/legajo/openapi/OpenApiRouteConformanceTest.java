package co.edu.uniquindio.legajo.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task A6's route-drift half of TC-08: {@code docs/openapi-legajo.yaml} must name exactly
 * the (method, path) pairs the running application actually maps, in both directions — a
 * removed endpoint left in the YAML, or a new endpoint never added to it, must fail the
 * build (TAC-12, TRD ADR-010).
 *
 * <p>Two live sources feed the "actually maps" side, since Spring Boot registers
 * {@code /api/v1/**} controllers and the actuator health endpoint through two different
 * {@link RequestMappingInfoHandlerMapping} beans: {@link RequestMappingHandlerMapping} for
 * ordinary {@code @RestController}s, and {@link WebMvcEndpointHandlerMapping} for actuator.
 * {@code /api/v1/test-only/**} ({@code BuggyTestOnlyController}) is deliberately excluded —
 * it is a test-only fixture that only exists on the test classpath to exercise error
 * mappings (see its own Javadoc), never a documented endpoint — and so is actuator's own
 * {@code /actuator} link-discovery root, which TRD §6.6's endpoint table never lists (only
 * {@code /actuator/health} is fixed there).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OpenApiRouteConformanceTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private WebMvcEndpointHandlerMapping webMvcEndpointHandlerMapping;

    @Test
    void theContractNamesExactlyTheRoutesTheApplicationMaps() {
        Set<String> documented = documentedRoutes();
        // Non-empty guard (this project's recurring defect shape): a parser failure that
        // silently returned an empty path map must not make this assertion vacuously pass.
        assertThat(documented).isNotEmpty();

        Set<String> mapped = new TreeSet<>();
        mapped.addAll(routesOf(requestMappingHandlerMapping, path -> path.startsWith("/api/v1/")
                && !path.startsWith("/api/v1/test-only/")));
        mapped.addAll(routesOf(webMvcEndpointHandlerMapping, "/actuator/health"::equals));
        assertThat(mapped).isNotEmpty();

        assertThat(mapped).containsExactlyInAnyOrderElementsOf(documented);
    }

    private static Set<String> documentedRoutes() {
        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(specUrl(), null, null);
        OpenAPI api = result.getOpenAPI();
        assertThat(api).as("docs/openapi-legajo.yaml must parse: %s", result.getMessages()).isNotNull();

        Set<String> routes = new TreeSet<>();
        for (Map.Entry<String, PathItem> entry : api.getPaths().entrySet()) {
            String path = entry.getKey();
            entry.getValue().readOperationsMap()
                    .keySet()
                    .forEach(method -> routes.add(method.name() + " " + path));
        }
        return routes;
    }

    private static String specUrl() {
        return new File("docs/openapi-legajo.yaml").getAbsoluteFile().toURI().toString();
    }

    private static Set<String> routesOf(RequestMappingInfoHandlerMapping mapping, Predicate<String> pathFilter) {
        Set<String> routes = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : patternsOf(info)) {
                if (!pathFilter.test(pattern)) {
                    continue;
                }
                for (RequestMethod method : methods) {
                    routes.add(method.name() + " " + pattern);
                }
            }
        }
        return routes;
    }

    /** Spring Framework 7 always resolves route patterns through {@code PathPattern} (the
     * older, string-based {@code PatternsRequestCondition} is deprecated for removal). */
    private static Set<String> patternsOf(RequestMappingInfo info) {
        Set<String> patterns = new TreeSet<>();
        info.getPathPatternsCondition().getPatterns().forEach(pattern -> patterns.add(pattern.getPatternString()));
        return patterns;
    }
}
