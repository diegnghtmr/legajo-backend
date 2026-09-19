plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "legajo"

include("domain", "application", "infrastructure", "bootstrap", "benchmarks")
