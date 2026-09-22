// JMH harness: empirical complexity curves against TRD §6.3/§6.4 theoretical complexities
// (NFR-QA-10's fixed performance-test protocol), plus the SLO benchmarks for NFR-QA-01/02
// and a CSV/slopes exporter (odd/tasks/jmh-benchmarks.md, tasks J1/J2).

plugins {
    alias(libs.plugins.jmh)
}

dependencies {
    implementation(project(":domain"))
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
