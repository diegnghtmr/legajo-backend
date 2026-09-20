// Pure business logic. No Spring, no Jackson, no DJL — enforced by ArchUnit in :bootstrap.

import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

dependencies {
    api(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// NFR-QA-06 / TAC-08 (TRD §15): "Cobertura de líneas > 85 % en esos paquetes", where
// "esos paquetes" are the three named in NFR-QA-06's Artefacto row: similarity, clustering,
// evaluation. The rule is scoped to exactly those packages, not to this module as a whole
// (this module also holds preprocess/corpus/port, which the TRD does not name here) and not
// to an aggregate across the multi-module build (jacocoRootReport already aggregates the
// whole codebase for reporting, which is a broader scope than the TRD's gate). `check`
// depends on this task so a coverage regression fails `./gradlew build`, not just the report.
val algorithmPackagePaths = listOf(
    "co/edu/uniquindio/legajo/similarity/**",
    "co/edu/uniquindio/legajo/clustering/**",
    "co/edu/uniquindio/legajo/evaluation/**",
)

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        classDirectories.files.map { dir -> fileTree(dir) { include(algorithmPackagePaths) } },
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
