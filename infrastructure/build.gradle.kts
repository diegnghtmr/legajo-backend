// Adapters: REST, PDF, embeddings, corpus JSON, caches. Depends on :application and :domain.
// T4 adds the corpus JSON repository and the PDF extraction adapters (GROBID + PDFBox);
// remaining adapters (REST, DJL, OpenAI, Caffeine) land with their own tasks.

plugins {
    alias(libs.plugins.spring.dependency.management)
}

// Jackson 3 (Spring Boot 4's default JSON library, package tools.jackson.*) has no
// standalone BOM alias in the catalog; importing the Spring Boot BOM keeps its version
// aligned with the Boot line this backend targets (Boot 4.0.3 -> jackson-bom 3.0.4)
// without hand-pinning a second version.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}

dependencies {
    api(project(":domain"))
    api(project(":application"))

    implementation(platform(libs.spring.ai.bom))

    // REST adapters (A3/A4 of the rest-api feature) live in this module (TRD §6.6); exposed
    // as `api` so the embedded servlet container it brings (Tomcat + spring-webmvc) also
    // lands on :bootstrap's runtime classpath transitively, giving the composition root an
    // actual HTTP port to answer /actuator/health on (A1). :bootstrap declares no direct
    // dependency of its own on this starter for that reason.
    api(libs.spring.boot.starter.web)

    implementation(libs.jackson.databind)
    implementation(libs.caffeine)
    implementation(libs.pdfbox)
    // MiniLM offline precompute (TRD S5.1, S6.3): the HuggingFace tokenizer is delegable
    // under R-02; ONNX Runtime only runs the model's matrix inference, never the pooling or
    // the metric, which stay hand-written in domain/EmbeddingLocal and this module's
    // MiniLmEmbedder.
    implementation(libs.djl.tokenizers)
    implementation(libs.onnxruntime)
    // embedding-api offline precompute (TRD §6.3, §8, ADR-015): Spring AI's bare OpenAI
    // module (not the Boot starter — this adapter builds OpenAiEmbeddingModel
    // programmatically, the same plain-CLI style as MiniLmEmbedder) talks to Gemini's
    // OpenAI-compatible embeddings endpoint. Model inference is delegable under R-02; the L2
    // normalization and the Euclidean metric stay hand-written in domain/EmbeddingApi and
    // this module's OpenAiCompatibleEmbedder.
    implementation(libs.spring.ai.openai)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // WireMock covers the OpenAiCompatibleEmbedder adapter: success/5xx/timeout mapping
    // (TRD §13, "Adaptador remoto").
    testImplementation(libs.wiremock)
    testRuntimeOnly(libs.junit.platform.launcher)
}
