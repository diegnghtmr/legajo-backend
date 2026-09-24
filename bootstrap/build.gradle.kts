// Composition root: Spring wiring, profiles, startup. Depends on every other module.

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

// Resolved the ordinary Gradle way (a dependency declaration, not reaching into
// :infrastructure's own configurations object) for the precomputeEmbeddings task below:
// :infrastructure carries no Spring dependency at all, unlike this module's own
// runtimeClasspath (see that task's comment for why this separate classpath exists).
val precomputeRuntimeClasspath: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)
    // Publishes the API contract and Swagger UI. Swagger UI's descriptor URL is pointed at the hand-written
    // docs/openapi-legajo.yaml (application.yml's springdoc.swagger-ui.url, served as a
    // static resource by the copy below); springdoc's own generated /v3/api-docs document
    // stays enabled only because springdoc itself requires it internally to serve the UI
    // shell — it is never used as a source of truth, and never used by the drift tests
    // below, which compare the YAML directly against RequestMappingHandlerMapping.
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    precomputeRuntimeClasspath(project(":infrastructure"))

    testImplementation(libs.spring.boot.starter.test)
    // MockMvc controller tests: @WebMvcTest/@AutoConfigureMockMvc
    // live here, not in spring-boot-starter-test, as of Spring Boot 4.0.3 (see the version
    // catalog comment on this alias).
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.archunit.junit5)
    // Drift check: validates real MockMvc responses against
    // docs/openapi-legajo.yaml (see the version catalog comment on this alias for the
    // Spring Boot 4 / Jakarta compatibility check).
    testImplementation(libs.openapi.request.validator.mockmvc)
    // Live embedding-api mode: a real Spring context
    // with legajo.embedding-provider=live, wired against a stubbed OpenAI-compatible
    // embeddings endpoint via @DynamicPropertySource, so no test needs a real key or network.
    testImplementation(libs.wiremock)
    // Test-only, on :bootstrap's test classpath so ArchitectureTest's @AnalyzeClasses can
    // see :benchmarks' classes; without it the module escaped the architecture test suite
    // entirely (it was never a dependency of the one module ArchUnit runs from).
    testImplementation(project(":benchmarks"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// docs/openapi-legajo.yaml, one level up from this module, is the
// contract's single source of truth. Rather than hand-duplicating it onto the classpath,
// this copies that exact file into the module's own resources at build time, into
// static/, so Spring Boot's own static-resource handler serves it at
// GET /openapi-legajo.yaml with no further configuration, and springdoc/Swagger UI can
// point at it (application.yml) — the served document is always byte-identical to the
// authored file, never a second copy that can drift.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.layout.projectDirectory.file("docs/openapi-legajo.yaml")) {
        into("static")
    }
}

// The Spring context this module boots reads
// data/corpus.json and data/embeddings-*.json through relative paths, the same convention
// the ingest/verify/validate/precompute JavaExec tasks above already use. Both `test` (so
// @SpringBootTest can load the real, versioned demo corpus) and `bootRun` (so a developer
// running `./gradlew :bootstrap:bootRun` from the backend root gets the same resolution)
// need the same fix: Gradle's default working directory
// for a subproject's JavaExec-derived task is that subproject's own directory
// (`backend/bootstrap`), not the directory the `gradlew` invocation started from, and
// `data/` lives at the backend root, one level up.
tasks.named<Test>("test") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

// Pins the boot jar's file name so the Dockerfile's COPY line never has to hard-code (and
// keep in sync by hand) the project version embedded in Spring Boot's default archive name
// (bootstrap-${version}.jar). A version bump would otherwise silently break that COPY the
// next time the image is built.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

// Ingestion entry points. Plain JavaExec tasks rather than Spring profiles: each
// CLI is a one-shot offline batch job (see IngestCli's Javadoc), and JavaExec gets
// `--args="..."` support from Gradle for free. Working directory is the backend project
// root, not :bootstrap's own directory, so the documented relative paths
// (`data/pdfs`, `data/corpus.json`) resolve the way the command examples expect.
tasks.register<JavaExec>("ingest") {
    group = "ingestion"
    description = "Runs the offline PDF -> corpus.json pipeline. " +
            "Args: --input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070"
    mainClass.set("co.edu.uniquindio.legajo.IngestCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("verifyCorpus") {
    group = "verification"
    description = "Runs verify-corpus against data/corpus.json. Args: --corpus=data/corpus.json"
    mainClass.set("co.edu.uniquindio.legajo.VerifyCorpusCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("validateCorpus") {
    group = "verification"
    description = "Marks documents manuallyValidated=true. Args: --ids=d01,d02 or --all"
    mainClass.set("co.edu.uniquindio.legajo.ValidateCorpusCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("precomputeEmbeddings") {
    group = "ingestion"
    description = "Runs the offline MiniLM precompute. " +
            "Args: --corpus=data/corpus.json --output=data/embeddings-minilm.json " +
            "(tokenizer/model download to build/models/minilm/ on first run)"
    mainClass.set("co.edu.uniquindio.legajo.PrecomputeMiniLmEmbeddingsCli")
    // Deliberately NOT sourceSets["main"].runtimeClasspath: that classpath carries Spring
    // Boot's actuator/micrometer stack, which this plain-main CLI never uses, and which
    // segfaults the JVM when loaded alongside both the tokenizers and ONNX Runtime native
    // libraries in the same process (reproduced empirically; root cause not identified
    // beyond "micrometer's jars present" — see PrecomputeMiniLmEmbeddingsCli's Javadoc).
    // precomputeRuntimeClasspath carries domain+application+Jackson+PDFBox+the DJL
    // tokenizer+ONNX Runtime, with no Spring dependency at all.
    classpath = sourceSets["main"].output + precomputeRuntimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
    // ONNX Runtime loads its native library via System.load, a JEP 472 restricted method on
    // Java 25; this silences the resulting warning for this one-shot offline batch job.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.register<JavaExec>("precomputeApiEmbeddings") {
    group = "ingestion"
    description = "Runs the offline embedding-api precompute against Gemini's OpenAI-compatible " +
            "layer. Args: --corpus=data/corpus.json " +
            "--output=data/embeddings-openai.json. Requires SPRING_AI_OPENAI_API_KEY, " +
            "SPRING_AI_OPENAI_BASE_URL, LEGAJO_EMBEDDING_API_MODEL, LEGAJO_EMBEDDING_API_DIMENSION " +
            "(and optionally SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH) in the environment."
    mainClass.set("co.edu.uniquindio.legajo.PrecomputeApiEmbeddingsCli")
    // Unlike precomputeEmbeddings (MiniLM), this CLI never loads ONNX Runtime or the
    // HuggingFace tokenizer's native library — it only makes HTTP calls through Spring AI's
    // OpenAI client — so the ordinary Spring-ful runtime classpath is safe here (no segfault
    // risk from native libraries alongside actuator/micrometer, see
    // PrecomputeMiniLmEmbeddingsCli's Javadoc for that unrelated interaction).
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}
