package co.edu.uniquindio.legajo.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces this codebase's hexagonal dependency rules:
 * domain depends on nothing, application depends on domain, infrastructure depends on
 * application and domain, and bootstrap depends on everything. Runs against the whole
 * module classpath because {@code :bootstrap} is the only module that pulls in every
 * other module as a dependency (including, as of this rule, {@code :benchmarks}, added
 * as a test-only dependency purely so its classes are on this classpath for analysis).
 */
@AnalyzeClasses(packages = "co.edu.uniquindio.legajo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "co.edu.uniquindio.legajo";

    private static final String[] DOMAIN_PACKAGES = {
            BASE + ".similarity..",
            BASE + ".clustering..",
            BASE + ".evaluation..",
            BASE + ".preprocess..",
            BASE + ".corpus..",
            BASE + ".port.."
    };

    private static final String BENCHMARKS_PACKAGE = BASE + ".benchmarks..";

    @ArchTest
    static final ArchRule domainHasNoFrameworkDependency =
            noClasses().that().resideInAnyPackage(DOMAIN_PACKAGES)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "com.fasterxml.jackson..", "ai.djl..");

    @ArchTest
    static final ArchRule layerDependenciesAreRespected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy(DOMAIN_PACKAGES)
            .layer("Application").definedBy(BASE + ".application..")
            .layer("Infrastructure").definedBy(BASE + ".infrastructure..")
            .layer("Bootstrap").definedBy(BASE, BASE + ".architecture..")
            .layer("Benchmarks").definedBy(BENCHMARKS_PACKAGE)

            .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Bootstrap")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Bootstrap")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Bootstrap", "Benchmarks")
            .whereLayer("Benchmarks").mayNotBeAccessedByAnyLayer();
}
