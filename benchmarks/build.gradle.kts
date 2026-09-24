// JMH harness: empirical complexity curves measured against each algorithm's theoretical
// complexity, using a fixed performance-test protocol, plus dedicated SLO (service-level
// objective) benchmarks against the real reference corpus, and a CSV/slopes exporter that
// compares each measured growth rate to its expected one.

// Plain imports, not fully-qualified inline references: Gradle's Kotlin DSL exposes a `java(...)`
// extension function on `Project` for configuring `JavaPluginExtension`, which shadows the root
// `java` package when a fully-qualified name like `java.security.MessageDigest` is used inline in
// an expression (unlike a type position such as `java.io.File`, which resolves without issue).
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    alias(libs.plugins.jmh)
    alias(libs.plugins.spring.dependency.management)
}

// Jackson 3 (package tools.jackson.*) has no standalone BOM alias in the catalog; importing
// the Spring Boot BOM keeps this module's Jackson version aligned with the rest of the
// backend (infrastructure/build.gradle.kts uses the identical import for the identical
// reason). Used here only to read data/corpus.json for the real-corpus benchmarks and JMH's
// own JSON results file for the CSV/slopes exporter -- never for computing an algorithm's
// actual result, which this module only ever delegates to :domain for.
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

    // The fixed benchmarking protocol (AverageTime, 1 fork, 3x1s warmup, 5x1s measurement) is
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

// The me.champeau.jmh plugin never wires its own compile task into `check`/`build`, so a
// plain `./gradlew build` silently never compiled this module's JMH sources -- a broken
// @Benchmark class could sit unnoticed until someone ran :benchmarks:jmh by hand. Making
// `check` depend on `jmhClasses` (jmh's own "compile + assemble the jmh source set" task)
// closes that gap without running any benchmark in ordinary CI -- the JMH job itself stays
// manual/tag-triggered; only *compiling* it becomes part of the normal build.
tasks.named("check") {
    dependsOn("jmhClasses")
}

// Export-strictness hardening slice: the harness must describe the machine that actually
// produced the numbers, so it is captured right when :benchmarks:jmh runs -- never later, when
// :benchmarks:jmhExport happens to run on a different machine or session. Gradle's
// `finalizedBy` always runs a finalizer, even when the finalized task fails, so this task's
// own `onlyIf` below skips it when :benchmarks:jmh itself failed -- otherwise a rerun after a
// failed jmh task would still capture a "fresh-looking" sidecar next to a stale, unrelated
// jmh-results.json left over from an earlier successful run. When it does run, the
// sidecar it writes is bound (by SHA-256, not by timestamp) to the exact
// jmh-results.json it describes; :benchmarks:jmhExport reads that binding, and the sidecar's
// fields, instead of calling HarnessInfo.collect() itself.
//
// Two independent guards, not one, make it impossible to bind a stale run's success marker to
// a fresh (or merely still-present) JSON on a different machine:
//
// 1. :benchmarks:jmh itself (below) is marked `upToDateWhen { false }` / `cacheIf { false }`:
//    Gradle can never report it UP-TO-DATE or FROM-CACHE, so whenever it is part of the task
//    graph its `doFirst`/`doLast` always both run for real. A prior design left the marker
//    file's mere existence as the only signal, reasoning that a skipped jmh run leaves "no
//    fresh marker" for the sidecar to find -- that reasoning was wrong: a skipped run leaves
//    the *previous* successful run's marker sitting there untouched, which still exists, so
//    the sidecar's old existence-only `onlyIf` still ran and captured the current machine
//    against whatever stale jmh-results.json happened to be on disk. Forcing jmh to always
//    execute when scheduled removes that failure mode outright: there is no "skipped but the
//    marker survives" case left to guard against.
// 2. As defense in depth for any other way the marker and the JSON could end up mismatched
//    (a partial run, a manual copy, invoking `jmhHarnessSidecar` on its own against leftover
//    build output), the marker itself is no longer an empty flag file: :benchmarks:jmh writes
//    the SHA-256 of the exact jmh-results.json it just produced into it, and the sidecar's
//    `onlyIf` recomputes that JSON's current SHA-256 and only proceeds when the two match. A
//    marker whose recorded hash does not match the JSON currently on disk can never describe
//    that JSON, no matter how it got there.
//
// The `onlyIf` below must decide this without reading :benchmarks:jmh's own `Task.state`:
// under the configuration cache, a task action can only use values captured at configuration
// time, and a live `Task` reference (or the `Project` it drags in through `tasks.named(...)`)
// is not one of them -- `--configuration-cache --dry-run` reports it as an unsupported
// `DefaultProject` reference. Both the marker file and the JSON file it is checked against are
// plain `File` values, not `Task` references, so they stay config-cache-safe to capture. Each
// task below computes its own local `File` references (rather than sharing one top-level script
// property) so the lambdas below capture only those plain values, never an implicit reference
// to this build script object.
fun jmhSuccessMarkerFile(): java.io.File = layout.buildDirectory.file("results/jmh/.jmh-succeeded").get().asFile
fun jmhResultsJsonFile(): java.io.File = layout.buildDirectory.file("results/jmh/jmh-results.json").get().asFile

// Locally computed rather than delegating to `HarnessInfo.sha256Hex`: this build script's
// classpath does not include this module's own `main` source set, only the reverse.
fun sha256Hex(file: java.io.File): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    return HexFormat.of().formatHex(digest)
}

tasks.register<JavaExec>("jmhHarnessSidecar") {
    group = "verification"
    description = "Captures the reference-harness metadata right when :benchmarks:jmh runs, into " +
            "build/results/jmh/harness.properties, for :benchmarks:jmhExport to read later."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.edu.uniquindio.legajo.benchmarks.export.HarnessSidecarCli")
    args(
        "--output=${layout.buildDirectory.file("results/jmh/harness.properties").get().asFile}",
        "--input=${layout.buildDirectory.file("results/jmh/jmh-results.json").get().asFile}",
    )
    val successMarker = jmhSuccessMarkerFile()
    val resultsJson = jmhResultsJsonFile()
    onlyIf("the :benchmarks:jmh task must have succeeded and the marker must be bound to the " +
            "exact jmh-results.json currently on disk") {
        successMarker.isFile && resultsJson.isFile && successMarker.readText() == sha256Hex(resultsJson)
    }
}

// The marker is only ever (re)written by this task's own `doLast`, and only ever records the
// SHA-256 of the jmh-results.json this exact run produced. `upToDateWhen { false }` and
// `cacheIf { false }` below mean Gradle never treats this task as UP-TO-DATE or FROM-CACHE, so
// whenever :benchmarks:jmh is scheduled it always truly executes -- `doFirst` always deletes any
// marker left over from a previous run before this run starts, and `doLast` always writes a
// fresh one bound to this run's own JSON before finishing. Once a sidecar is captured, the
// SHA-256 binding in HarnessInfo/JmhExportCli guards jmhExport's own read of it; the marker's own
// SHA-256 binding (checked by jmhHarnessSidecar's `onlyIf` above) is what stops that sidecar from
// ever being captured against a JSON the marker does not actually describe.
tasks.named("jmh") {
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    val successMarker = jmhSuccessMarkerFile()
    val resultsJson = jmhResultsJsonFile()
    doFirst {
        successMarker.delete()
    }
    doLast {
        successMarker.parentFile.mkdirs()
        successMarker.writeText(sha256Hex(resultsJson))
    }
    finalizedBy("jmhHarnessSidecar")
}

// Exports the last JMH run (build/results/jmh/jmh-results.json) into the two versioned
// CSVs the technical documentation reads: benchmarks/results/jmh-results.csv and
// benchmarks/results/slopes.csv. Run after :benchmarks:jmh, e.g.:
//   ./gradlew :benchmarks:jmh :benchmarks:jmhExport
// The export itself is strict: it fails before writing either CSV if the harness sidecar is
// missing or bound to a different JMH results file, its JDK disagrees with what the JMH JSON
// itself reports, or any benchmark result cannot be classified.
tasks.register<JavaExec>("jmhExport") {
    group = "verification"
    description = "Exports build/results/jmh/jmh-results.json to benchmarks/results/jmh-results.csv " +
            "and benchmarks/results/slopes.csv. Run :benchmarks:jmh first."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.edu.uniquindio.legajo.benchmarks.export.JmhExportCli")
    // Ordered after :benchmarks:jmh without forcing a JMH run: jmhExport alone can still
    // re-export an existing build/results/jmh/jmh-results.json.
    mustRunAfter("jmh")
    args(
        "--input=${layout.buildDirectory.file("results/jmh/jmh-results.json").get().asFile}",
        "--harness=${layout.buildDirectory.file("results/jmh/harness.properties").get().asFile}",
        "--resultsCsv=${projectDir}/results/jmh-results.csv",
        "--slopesCsv=${projectDir}/results/slopes.csv",
    )
}
