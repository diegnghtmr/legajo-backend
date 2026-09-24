import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("jacoco")
}

allprojects {
    group = "co.edu.uniquindio.legajo"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("legajo.javaToolchain").get().toInt()))
        }
    }

    configure<JacocoPluginExtension> {
        toolVersion = rootProject.libs.versions.jacoco.get()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Feature doc `rest-api.md`, task A3b: without this flag, javac discards method
    // parameter names, so an unnamed `@PathVariable`/`@RequestParam` throws
    // IllegalArgumentException at request time instead of failing the build. Applied once
    // here so every subproject's compiled classes carry parameter names (verified by
    // infrastructure's CompilerParametersFlagTest via reflection), rather than relying on
    // every controller author remembering to name every parameter explicitly on its
    // annotation, as task A3 did as a workaround.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

/**
 * Aggregated coverage across every subproject that has produced test execution data.
 * The algorithm packages require >85% coverage; this task is the single
 * entry point CI and local builds use to compute that coverage report.
 */
tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregates JaCoCo coverage across all subprojects into a single report."

    val testedProjects = subprojects.filter { it.tasks.findByName("test") != null }

    dependsOn(testedProjects.map { it.tasks.named("test") })

    val subprojectClassDirs = testedProjects.map { proj ->
        proj.layout.buildDirectory.dir("classes/java/main")
    }
    val subprojectSourceDirs = testedProjects.map { it.layout.projectDirectory.dir("src/main/java") }
    val subprojectExecutionDataFiles = testedProjects.map { proj ->
        proj.layout.buildDirectory.file("jacoco/test.exec")
    }

    classDirectories.setFrom(files(subprojectClassDirs))
    sourceDirectories.setFrom(files(subprojectSourceDirs))
    executionData.setFrom(files(subprojectExecutionDataFiles).filter { it.exists() })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
