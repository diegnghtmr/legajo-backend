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

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

/**
 * Aggregated coverage across every subproject that has produced test execution data.
 * NFR-QA-06 requires >85% coverage in the algorithm packages; this task is the single
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
