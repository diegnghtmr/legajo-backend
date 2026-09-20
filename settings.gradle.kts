// The version catalog (gradle/libs.versions.toml) is not reachable from this top-level
// plugins {} block — Gradle only exposes it to pluginManagement { plugins {} } and to
// project build scripts, not here — so this plugin's id and version are declared once,
// directly, in the only place they can be.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "legajo"

include("domain", "application", "infrastructure", "bootstrap", "benchmarks")
