// Adapters: REST, PDF, embeddings, corpus JSON, caches. Depends on :application and :domain.
// No adapter is implemented yet (T1 is scaffold-only); Spring/Jackson/DJL adapter
// dependencies are added when each adapter lands (T4+).

dependencies {
    api(project(":domain"))
    api(project(":application"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
