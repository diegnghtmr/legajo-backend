// JMH harness: empirical complexity curves against TRD §6.3/§6.4 theoretical complexities
// (NFR-QA-10's fixed performance-test protocol), plus the SLO benchmarks for NFR-QA-01/02
// and a CSV/slopes exporter (odd/tasks/jmh-benchmarks.md, tasks J1/J2).

plugins {
    alias(libs.plugins.jmh)
    alias(libs.plugins.spring.dependency.management)
}

// Jackson 3 (package tools.jackson.*) has no standalone BOM alias in the catalog; importing
// the Spring Boot BOM keeps this module's Jackson version aligned with the rest of the
// backend (infrastructure/build.gradle.kts uses the identical import for the identical
// reason). Used here only to read data/corpus.json for the real-corpus benchmarks and JMH's
// own JSON results file for the CSV/slopes exporter (J2) -- never for an R-02-covered
// algorithm, which this module only ever calls into :domain for.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.jackson.databind)
    jmh(project(":domain"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

jmh {
    jmhVersion.set(libs.versions.jmh.get())
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("results/jmh/jmh-results.json"))

    // NFR-QA-10's fixed protocol (AverageTime, 1 fork, 3x1s warmup, 5x1s measurement) is
    // declared on every @Benchmark class itself (@Fork, @Warmup, @Measurement); the
    // properties below are deliberately left unset by default so those annotations govern a
    // full run, and only take effect when passed explicitly -- which is how a fast smoke run
    // shrinks the protocol without touching any benchmark class:
    //
    //   ./gradlew :benchmarks:jmh -Pjmh.fork=1 -Pjmh.warmupIterations=1 -Pjmh.iterations=1 \
    //       -Pjmh.includes=Levenshtein
    if (project.hasProperty("jmh.fork")) {
        fork.set((project.property("jmh.fork") as String).toInt())
    }
    if (project.hasProperty("jmh.warmupIterations")) {
        warmupIterations.set((project.property("jmh.warmupIterations") as String).toInt())
    }
    if (project.hasProperty("jmh.iterations")) {
        iterations.set((project.property("jmh.iterations") as String).toInt())
    }
    if (project.hasProperty("jmh.includes")) {
        includes.set(listOf(project.property("jmh.includes") as String))
    }
}

// J3: the me.champeau.jmh plugin never wires its own compile task into `check`/`build`, so a
// plain `./gradlew build` silently never compiled this module's JMH sources -- a broken
// @Benchmark class could sit unnoticed until someone ran :benchmarks:jmh by hand. Making
// `check` depend on `jmhClasses` (jmh's own "compile + assemble the jmh source set" task)
// closes that gap without running any benchmark in ordinary CI (TRD §14.3: the JMH job
// itself stays manual/tag-triggered; only *compiling* it becomes part of the normal build).
tasks.named("check") {
    dependsOn("jmhClasses")
}

// J2: exports the last JMH run (build/results/jmh/jmh-results.json) into the two versioned
// CSVs the technical documentation reads (TAC-18): benchmarks/results/jmh-results.csv and
// benchmarks/results/slopes.csv. Run after :benchmarks:jmh, e.g.:
//   ./gradlew :benchmarks:jmh :benchmarks:jmhExport
tasks.register<JavaExec>("jmhExport") {
    group = "verification"
    description = "Exports build/results/jmh/jmh-results.json to benchmarks/results/jmh-results.csv " +
            "and benchmarks/results/slopes.csv (TRD NFR-QA-10, TAC-18). Run :benchmarks:jmh first."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.edu.uniquindio.legajo.benchmarks.export.JmhExportCli")
    args(
        "--input=${layout.buildDirectory.file("results/jmh/jmh-results.json").get().asFile}",
        "--resultsCsv=${projectDir}/results/jmh-results.csv",
        "--slopesCsv=${projectDir}/results/slopes.csv",
    )
}
