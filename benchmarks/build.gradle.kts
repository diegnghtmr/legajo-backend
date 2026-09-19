// JMH harness: empirical complexity curves against TRD §6.3/§6.4 theoretical complexities.
// No benchmark exists yet (T1 is scaffold-only); benchmarks are added alongside each algorithm.

plugins {
    alias(libs.plugins.jmh)
}

dependencies {
    implementation(project(":domain"))
    jmh(project(":domain"))
}

jmh {
    jmhVersion.set(libs.versions.jmh.get())
}
