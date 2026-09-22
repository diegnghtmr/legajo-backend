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

CI always uses `actions/setup-java` (pinned to a commit SHA, currently v4.9.1) with
`distribution: temurin`, `java-version: "25"`.

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

`.env.example` is the declared list of variables this backend reads; copy it to `.env`
for local runs and fill in the values there (`.env` is git-ignored, values never go in
git — use the hosting provider's secret panel in production).

| Variable | Read by | Required | Notes |
|---|---|---|---|
| `LEGAJO_EMBEDDING_PROVIDER` | `application.yml` (`legajo.embedding-provider`) | No, defaults to `cached` | `cached` reads `data/embeddings-*.json`, no network needed |
| `LEGAJO_CORS_ORIGINS` | `application.yml` (`legajo.cors-origins`) | No, defaults to `http://localhost:5173` and `http://localhost` | Comma-separated browser origins allowed by CORS for `/api/v1/**`; a defined list replaces the defaults instead of adding to them (TRD §14.4) |
| `SPRING_AI_OPENAI_BASE_URL` | `PrecomputeApiEmbeddingsCli` | Only for `:bootstrap:precomputeApiEmbeddings` | OpenAI-compatible embeddings endpoint (Gemini, TRD §8) |
| `SPRING_AI_OPENAI_API_KEY` | `PrecomputeApiEmbeddingsCli` | Only for `:bootstrap:precomputeApiEmbeddings` | Read from the environment only; never logged or included in an exception message |
| `LEGAJO_EMBEDDING_API_MODEL` | `PrecomputeApiEmbeddingsCli` | Only for `:bootstrap:precomputeApiEmbeddings` | e.g. `gemini-embedding-2-preview` |
| `LEGAJO_EMBEDDING_API_DIMENSION` | `PrecomputeApiEmbeddingsCli` | Only for `:bootstrap:precomputeApiEmbeddings` | Must be a positive integer |
| `LEGAJO_GROBID_URL` | `IngestCli` | Only for `:bootstrap:ingest` | GROBID endpoint for ingestion (TRD §8). Resolves as `--grobid-url`, then this variable, then `http://localhost:8070`; a blank value counts as unset |
| `SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH` | `PrecomputeApiEmbeddingsCli` | No | Optional, warn-only: Spring AI 2.0.x has no override point to route it to, so this CLI only warns if it is set to something other than `/embeddings`; see the class's Javadoc for the full history |

## Ingestion, validation and verification (TRD §6.1)

The pipeline takes any folder of PDFs and writes `data/corpus.json`, replacing whatever
was there before. `data/pdfs/` is the reference corpus (20 teacher PDFs, never
committed); `data/corpus.json` and `data/embeddings-*.json` are the only generated files
that *are* committed, and only once every document has been reviewed and validated.

Three plain `main` entry points under `:bootstrap` (not Spring profiles/
`CommandLineRunner`s — see `IngestCli`'s Javadoc for why: each is a one-shot offline
batch job with no web or DI need). All commands run from `backend/`.

1. **Start GROBID** (primary extractor; only needed for ingestion, never for the demo):

   ```bash
   docker compose --profile ingest up -d grobid
   # wait until this returns "true":
   curl -s http://localhost:8070/api/isalive
   ```

2. **Ingest.** Scans `--input` for `*.pdf` sorted by name, extracts each through GROBID
   with a PDFBox fallback (used automatically on a GROBID failure *or* an empty
   abstract), applies the ingestion cleaning step, and writes `--output` with every
   document `manuallyValidated=false`:

   ```bash
   ./gradlew :bootstrap:ingest --args="--input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070"
   ```

3. **Review each abstract by hand.** This is the only mandatory control (TRD §6.1, item
   5) and is never automatic — inspect `title`/`authors`/`abstract` per document (e.g.
   by reading the freshly written `data/corpus.json`) before validating anything.

4. **Validate** the documents you reviewed, which freezes their `abstractSha256` and
   recomputes `corpusSha256`:

   ```bash
   ./gradlew :bootstrap:validateCorpus --args="--ids=d01,d02"
   # or, once every document has been reviewed:
   ./gradlew :bootstrap:validateCorpus --args="--all"
   ```

5. **Verify.** Exits non-zero and prints every violation (never just the first) if the
   corpus is invalid; for the reference corpus this must pass with `sourceCount=20`, 20
   documents, and every document `manuallyValidated=true`:

   ```bash
   ./gradlew :bootstrap:verifyCorpus
   ```

6. **Stop GROBID** once ingestion is done — it is not part of the `default` demo profile:

   ```bash
   docker compose --profile ingest down
   ```

Re-running step 2 replaces `data/corpus.json` outright (TRD §3.1): every document goes
back to `manuallyValidated=false`, and any previously computed `data/embeddings-*.json`
caches become stale (their `corpusSha256` no longer matches) until embeddings are
recomputed for the new corpus.

## Benchmarks

`benchmarks/` measures the algorithms with JMH under the fixed protocol (TRD NFR-QA-10,
"Protocolo de pruebas de rendimiento" and "Arnés de referencia") and proves the performance
SLOs (TAC-07, NFR-QA-01/NFR-QA-02): `@BenchmarkMode(AverageTime)`, `@Fork(1)`,
`@Warmup(iterations = 3, time = 1)`, `@Measurement(iterations = 5, time = 1)` on every
benchmark class.

| Family | Classes | What varies |
|---|---|---|
| Pairwise classic curves | `pairwise.LevenshteinBenchmark`, `NeedlemanWunschBenchmark`, `JaccardBenchmark`, `TfIdfCosineBenchmark` | Token-sequence length L ∈ {50, 100, 200, 400, 800}, built from real corpus tokens |
| HAC curves | `hac.LanceWilliamsBenchmark` (× 4 linkage criteria), `hac.InternalMetricsBenchmark` (mean silhouette, Davies-Bouldin) | n ∈ {5, 10, 20, 40, 80} synthetic unit vectors |
| Embedding primitive | `embedding.EmbeddingPrimitiveBenchmark` | d ∈ {384, 1536}, one measurement each, no curve |
| SLO benchmarks | `slo.ClassicPairwiseSloBenchmark` (NFR-QA-01, per classic algorithm), `slo.ClusteringSloBenchmark` (NFR-QA-02, all four linkages) | Fixed at the real reference corpus, n = 20, similarity cache never involved |

Run the full protocol and export the CSVs:

```bash
./gradlew :benchmarks:jmh :benchmarks:jmhExport
```

Results land in `benchmarks/results/`, versioned in git:

- `jmh-results.csv` — `#`-prefixed harness header (CPU model, logical cores, total RAM, JDK,
  OS, UTC date) followed by `benchmark,family,parameter,size,score,error,unit` rows.
- `slopes.csv` — `family,points,empiricalSlope,theoreticalExponent`: the least-squares
  log-log slope of each curve next to the theoretical complexity TRD §6.3/§6.4/§6.5 document
  for that family (TAC-18). A fixed-n SLO family has no theoretical exponent and is excluded.

A fast, non-representative smoke run (shrinks the protocol; never commit its numbers) is
available by overriding the JMH Gradle plugin's properties and narrowing to a benchmark
subset with a regex:

```bash
./gradlew :benchmarks:jmh -Pjmh.fork=1 -Pjmh.warmupIterations=1 -Pjmh.iterations=1 \
    -Pjmh.includes=Levenshtein
```

The reference harness is the machine recorded in `jmh-results.csv`'s header; TRD NFR-QA-10's
baseline target is a 4 vCPU / 8 GB x86-64 machine. `.github/workflows/benchmarks.yml` runs the
JMH suite manually (`workflow_dispatch`) or on a `bench-*` tag push, never on every push or
pull request (TRD §14.3), and uploads `jmh-results.json` and the two CSVs as a build artifact.
**The numbers that job produces are not the reference harness**: it runs on a shared,
unpinned GitHub-hosted runner, not the documented machine. The versioned CSVs in
`benchmarks/results/` — the ones the technical documentation cites (TAC-07, TAC-18) — always
come from a local run on the reference harness, never from CI.
