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

    precomputeRuntimeClasspath(project(":infrastructure"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
    // Test-only, on :bootstrap's test classpath so ArchitectureTest's @AnalyzeClasses can
    // see :benchmarks' classes; without it the module escaped the architecture test suite
    // entirely (it was never a dependency of the one module ArchUnit runs from).
    testImplementation(project(":benchmarks"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The Spring context this module now boots (A1 of the rest-api feature) reads
// data/corpus.json and data/embeddings-*.json through relative paths, the same convention
// the ingest/verify/validate/precompute JavaExec tasks above already use. Both `test` (so
// @SpringBootTest can load the real, versioned demo corpus) and `bootRun` (so a developer
// running `./gradlew :bootstrap:bootRun` from the backend root gets the same resolution as
// the documented command in AGENTS.md) need the same fix: Gradle's default working directory
// for a subproject's JavaExec-derived task is that subproject's own directory
// (`backend/bootstrap`), not the directory the `gradlew` invocation started from, and
// `data/` lives at the backend root, one level up.
tasks.named<Test>("test") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.layout.projectDirectory.asFile
}

// TRD §6.1 ingestion entry points. Plain JavaExec tasks rather than Spring profiles: each
// CLI is a one-shot offline batch job (see IngestCli's Javadoc), and JavaExec gets
// `--args="..."` support from Gradle for free. Working directory is the backend project
// root, not :bootstrap's own directory, so the documented relative paths
// (`data/pdfs`, `data/corpus.json`) resolve the way the command examples expect.
tasks.register<JavaExec>("ingest") {
    group = "ingestion"
    description = "Runs the offline PDF -> corpus.json pipeline (TRD §6.1). " +
            "Args: --input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070"
    mainClass.set("co.edu.uniquindio.legajo.IngestCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("verifyCorpus") {
    group = "verification"
    description = "Runs verify-corpus against data/corpus.json (TRD §6.1, item 6). Args: --corpus=data/corpus.json"
    mainClass.set("co.edu.uniquindio.legajo.VerifyCorpusCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("validateCorpus") {
    group = "verification"
    description = "Marks documents manuallyValidated=true (TRD §6.1, item 5). Args: --ids=d01,d02 or --all"
    mainClass.set("co.edu.uniquindio.legajo.ValidateCorpusCli")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
}

tasks.register<JavaExec>("precomputeEmbeddings") {
    group = "ingestion"
    description = "Runs the offline MiniLM precompute (TRD §6.1, §6.3, §9). " +
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
            "layer (TRD §6.1, §6.3, §8, §9, ADR-015). Args: --corpus=data/corpus.json " +
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
