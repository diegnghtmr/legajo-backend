// Composition root: Spring wiring, profiles, startup. Depends on every other module.

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.actuator)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
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
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.layout.projectDirectory.asFile
    // ONNX Runtime loads its native library via System.load, a JEP 472 restricted method on
    // Java 25; this silences the resulting warning for this one-shot offline batch job.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
