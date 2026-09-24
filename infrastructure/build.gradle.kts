// Adapters: REST, PDF, embeddings, corpus JSON, caches. Depends on :application and :domain.
// The corpus JSON repository and the PDF extraction adapters (GROBID + PDFBox) were added
// alongside the remaining adapters (REST, DJL, OpenAI, Caffeine).

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

    // The REST adapters live in this module; exposed as `api` so the embedded servlet
    // container it brings (Tomcat + spring-webmvc) also lands on :bootstrap's runtime
    // classpath transitively, giving the composition root an actual HTTP port to answer
    // /actuator/health on. :bootstrap declares no direct dependency of its own on this
    // starter for that reason.
    api(libs.spring.boot.starter.web)

    implementation(libs.jackson.databind)
    implementation(libs.caffeine)
    implementation(libs.pdfbox)
    // MiniLM offline precompute: using the HuggingFace tokenizer and ONNX Runtime for model
    // inference is fine, since only the algorithm's own metric and normalization math must
    // be hand-written rather than delegated to a library. ONNX Runtime only runs the
    // model's matrix inference, never the pooling or the metric, which stay hand-written in
    // domain/EmbeddingLocal and this module's MiniLmEmbedder.
    implementation(libs.djl.tokenizers)
    implementation(libs.onnxruntime)
    // embedding-api offline precompute: Spring AI's bare OpenAI module (not the Boot
    // starter — this adapter builds OpenAiEmbeddingModel programmatically, the same
    // plain-CLI style as MiniLmEmbedder) talks to Gemini's OpenAI-compatible embeddings
    // endpoint. Using a library for model inference is fine; the L2 normalization and the
    // Euclidean metric stay hand-written in domain/EmbeddingApi and this module's
    // OpenAiCompatibleEmbedder.
    implementation(libs.spring.ai.openai)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    // WireMock covers the OpenAiCompatibleEmbedder adapter: success/5xx/timeout mapping
    // for the remote embedding adapter.
    testImplementation(libs.wiremock)
    testRuntimeOnly(libs.junit.platform.launcher)
}
