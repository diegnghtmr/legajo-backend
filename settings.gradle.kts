plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "legajo"

include("domain", "application", "infrastructure", "bootstrap", "benchmarks")
