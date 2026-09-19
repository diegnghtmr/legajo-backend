# Legajo backend

Server for Legajo: six hand-written text-similarity capabilities, four hierarchical
clustering linkages, and their internal metrics, exposed over a REST API. See
`AGENTS.md` for the full module map and rules; product/technical decisions live in the
workspace's `../docs/` (not part of this repository).

## Stack

- Java 25 LTS, Spring Boot 4.0.x (Spring Framework 7, JUnit 6)
- Gradle 9.2.x, Kotlin DSL, multimodule
- Hexagonal architecture, enforced by ArchUnit at build time
- JaCoCo aggregated coverage, GitHub Actions CI

## Modules

| Module | Depends on | Purpose |
|---|---|---|
| `domain` | nothing | Algorithms, preprocessing, metrics, ports. Zero Spring/Jackson/DJL |
| `application` | `domain` | Use cases orchestrating the domain |
| `infrastructure` | `application`, `domain` | Adapters: REST, PDF, embeddings, corpus JSON, caches |
| `bootstrap` | all | Spring composition root, configuration, startup |
| `benchmarks` | `domain` | JMH harness for empirical complexity curves |

Root package: `co.edu.uniquindio.legajo`.

## JDK 25 note

The machine's default JDK may be newer than 25 (Gradle 9.2 targets Java 25; AV-06
warns Gradle 9.0 rejects JDK 25, so 9.1.0+ is required). Two ways to build reproducibly:

1. **mise (recommended).** `.mise.toml` pins `java = "temurin-25.0.4+7.0.LTS"` and
   `gradle = "9.2.1"`. Run:

   ```bash
   mise install
   mise exec -- ./gradlew build
   ```

2. **Gradle toolchain auto-provisioning.** `settings.gradle.kts` applies the Foojay
   Toolchains Resolver, so a plain `./gradlew build` auto-downloads a JDK 25 toolchain
   if none is available locally, independent of the JDK running Gradle itself.

CI always uses `actions/setup-java@v4` with `distribution: temurin`, `java-version: "25"`.

## Build & test

```bash
./gradlew build            # compile, unit tests, ArchUnit
./gradlew test             # tests only
./gradlew jacocoRootReport # aggregated coverage across all modules
```

Start locally without network access:

```bash
LEGAJO_EMBEDDING_PROVIDER=cached ./gradlew :bootstrap:bootRun
```

Health check: `GET /actuator/health`.

## Environment variables

See `.env.example`: `LEGAJO_EMBEDDING_PROVIDER`, `LEGAJO_CORS_ORIGINS`,
`SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_API_KEY`. Values never go in git; use a
local `.env` or the hosting provider's secret panel.
