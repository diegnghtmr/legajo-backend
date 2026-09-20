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

    implementation(libs.jackson.databind)
    implementation(libs.pdfbox)
    // MiniLM offline precompute (TRD S5.1, S6.3): the HuggingFace tokenizer is delegable
    // under R-02; ONNX Runtime only runs the model's matrix inference, never the pooling or
    // the metric, which stay hand-written in domain/EmbeddingLocal and this module's
    // MiniLmEmbedder.
    implementation(libs.djl.tokenizers)
    implementation(libs.onnxruntime)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
