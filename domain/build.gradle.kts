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

// Line coverage must exceed 85% in the algorithm packages named below: similarity,
// clustering, evaluation. The rule is scoped to exactly those packages, not to this module
// as a whole (this module also holds preprocess/corpus/port, which is not named here) and
// not to an aggregate across the multi-module build (jacocoRootReport already aggregates the
// whole codebase for reporting, which is a broader scope than this gate). `check`
// depends on this task so a coverage regression fails `./gradlew build`, not just the report.
val algorithmPackages = listOf("similarity", "clustering", "evaluation")
val algorithmPackageRoot = "co/edu/uniquindio/legajo"
val algorithmPackagePaths = algorithmPackages.map { "$algorithmPackageRoot/$it/**" }

// The gate is checked per package, not over their union. A single union-wide ratio lets a
// well-covered package carry a bare one: with similarity at ~98% and ~800 lines, a brand-new
// clustering package could land at 0% and the union would still clear 85%. This rule gates
// those named packages — each of them — so each package answers for its own ratio. JaCoCo skips a
// limit whose counter total is zero, so the packages that hold only package-info.java today
// are not failed for being empty; they start answering the moment they hold real code.

// Where the algorithm sources actually are, discovered by package name rather than by the
// path above: a guard that trusted the same constant it is meant to check would go blind with
// it. A package that moves without the gate following is found here and fails the build.
val javaSourceRoot = file("src/main/java")
val gatedSourcePackagePaths = javaSourceRoot.walkTopDown()
    .filter { it.isDirectory && it.name in algorithmPackages }
    .filter { dir ->
        dir.listFiles { f -> f.isFile && f.name.endsWith(".java") && f.name != "package-info.java" }
            .orEmpty().isNotEmpty()
    }
    .map { it.relativeTo(javaSourceRoot).invariantSeparatorsPath }
    .toList()

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        classDirectories.files.map { dir -> fileTree(dir) { include(algorithmPackagePaths) } },
    )

    // A filter that matches nothing passes silently, and so does a per-package rule with no
    // package to apply to. Renaming or moving an algorithm package would therefore disable the
    // gate instead of failing it. Every package that holds real source must reach the verifier.
    val gatedPackages = gatedSourcePackagePaths
    val classTree = classDirectories
    doFirst {
        val analysedPaths = classTree.asFileTree.files.map { it.invariantSeparatorsPath }
        val unreached = gatedPackages.filter { pkg -> analysedPaths.none { it.contains("/$pkg/") } }
        check(unreached.isEmpty()) {
            "Coverage gate matched no compiled classes for algorithm package(s) $unreached. " +
                "Those packages hold source but `algorithmPackagePaths` no longer covers them, " +
                "so the gate would have passed without verifying anything."
        }
    }

    violationRules {
        rule {
            element = "PACKAGE"
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
