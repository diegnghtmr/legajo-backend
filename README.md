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

## Verification runs in containers (TRD §14.2)

**No check in this repository runs against a host JDK, host Gradle, or host `mise`
install.** Every build, test, and smoke check runs inside a container, always — a
developer machine only needs Docker. `.mise.toml` still pins `java =
"temurin-25.0.4+7.0.LTS"`/`gradle = "9.2.1"` and `settings.gradle.kts` still applies the
Foojay Toolchains Resolver, but both exist only so an IDE's language server has a local JDK
to point at for autocomplete/navigation — neither is a supported way to build or verify
this project. See "Checks run in containers" below for the container command for every
check, and `scripts/gradle-in-docker.sh` for the Gradle runner they share.

CI's `build` job runs on GitHub-hosted runners via `actions/setup-java` (pinned to a commit
SHA, currently v4.9.1) with `distribution: temurin`, `java-version: "25"` — a CI runner, not
a developer's machine, so this does not conflict with the container-only rule above. CI's
`image-smoke` job builds and exercises the image itself, the same way described below.

## Running with Docker

Build the image directly:

```bash
docker build -t legajo-backend:local .
```

Or run the `default`-profile demo stack with Compose (builds the image, publishes
`:8080`, waits for the healthcheck):

```bash
docker compose up -d --wait backend
docker compose down
```

Compose profiles (TRD §14.1):

| Profile | Services | Purpose |
|---|---|---|
| `default` (no `--profile` flag) | `backend` | The demo stack; `LEGAJO_EMBEDDING_PROVIDER` defaults to `cached`, no network needed |
| `ingest` | + `grobid` | One-shot corpus extraction only (see "Ingestion" below); never part of the demo |

`LEGAJO_EMBEDDING_PROVIDER` and `LEGAJO_CORS_ORIGINS` pass straight through from the host
environment into the `backend` service (`docker-compose.yml`); unset, they keep the
documented defaults (`cached`, and the two hard-coded CORS origins respectively).

Health check: `GET /actuator/health` (also the image's `HEALTHCHECK` and the Compose
service's inherited healthcheck — see the Dockerfile's own comments for why there is no
duplicate healthcheck definition in `docker-compose.yml`).

## Checks run in containers

Every check below is the exact command a developer or CI runs — no host JDK, no host
`npm`/`mise`, ever (TRD §14.2).

| Check | Container command |
|---|---|
| Full build (compile, unit tests, ArchUnit) | `./scripts/gradle-in-docker.sh build` |
| Tests only | `./scripts/gradle-in-docker.sh test` |
| Aggregated coverage (JaCoCo, >85% in the algorithm packages) | `./scripts/gradle-in-docker.sh jacocoRootReport` |
| Start locally, no network | `docker compose up -d --wait backend` |
| Image smoke test (health, OpenAPI, corpus, a real NW comparison, Ward clustering) | `docker compose up -d --wait backend && docker run --rm --network host -v "$(pwd)/scripts:/scripts:ro" -w /scripts alpine:3.20 sh -c 'apk add --no-cache curl jq >/dev/null && ./smoke.sh "$1"' _ http://localhost:8080; docker compose down` |
| JMH performance curves + CSV export (NFR-QA-10; must run on the reference machine — see "Benchmarks" below) | `./scripts/gradle-in-docker.sh :benchmarks:jmh :benchmarks:jmhExport` |

`scripts/gradle-in-docker.sh` runs the same pinned Temurin 25 JDK image the Dockerfile's
build stage uses, with the repository bind-mounted, a named volume for the Gradle cache,
and the host's Docker socket reachable (for a Testcontainers-backed adapter test, should one
be added — see the script's own comments).

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
   # wait until this returns "true" (containerized, no host curl — TRD §14.2):
   docker run --rm --network host alpine:3.20 sh -c 'apk add --no-cache curl >/dev/null && curl -s http://localhost:8070/api/isalive'
   ```

2. **Ingest.** Scans `--input` for `*.pdf` sorted by name, extracts each through GROBID
   with a PDFBox fallback (used automatically on a GROBID failure *or* an empty
   abstract), applies the ingestion cleaning step, and writes `--output` with every
   document `manuallyValidated=false`:

   ```bash
   ./scripts/gradle-in-docker.sh :bootstrap:ingest --args="--input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070"
   ```

3. **Review each abstract by hand.** This is the only mandatory control (TRD §6.1, item
   5) and is never automatic — inspect `title`/`authors`/`abstract` per document (e.g.
   by reading the freshly written `data/corpus.json`) before validating anything.

4. **Validate** the documents you reviewed, which freezes their `abstractSha256` and
   recomputes `corpusSha256`:

   ```bash
   ./scripts/gradle-in-docker.sh :bootstrap:validateCorpus --args="--ids=d01,d02"
   # or, once every document has been reviewed:
   ./scripts/gradle-in-docker.sh :bootstrap:validateCorpus --args="--all"
   ```

5. **Verify.** Exits non-zero and prints every violation (never just the first) if the
   corpus is invalid; for the reference corpus this must pass with `sourceCount=20`, 20
   documents, and every document `manuallyValidated=true`:

   ```bash
   ./scripts/gradle-in-docker.sh :bootstrap:verifyCorpus
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

Run the full protocol and export the CSVs, in the same Gradle session on the reference
machine (the harness is captured when `jmh` runs, not later when `jmhExport` runs, so both
must run on the same machine for the header to describe it correctly):

```bash
./scripts/gradle-in-docker.sh :benchmarks:jmh :benchmarks:jmhExport
```

Results land in `benchmarks/results/`, versioned in git:

- `jmh-results.csv` — `#`-prefixed harness header (CPU model, logical cores, total RAM, JDK,
  OS, UTC date) followed by `benchmark,family,parameter,size,score,error,unit` rows.
- `slopes.csv` — `family,points,empiricalSlope,theoreticalExponent`: the least-squares
  log-log slope of each curve next to the theoretical complexity TRD §6.3/§6.4/§6.5 document
  for that family (TAC-18). A fixed-n SLO family has no theoretical exponent and is excluded.

The export is strict: it writes neither CSV if any benchmark result cannot be classified into
a family (every skipped record is listed, with its reason, in the failure), if the
`build/results/jmh/harness.properties` sidecar `jmh` writes is missing or bound (by SHA-256) to
a different JMH results file than the one being exported, or if the JDK JMH itself reports
disagrees with that sidecar. `jmh`'s own sidecar-capturing step only runs when `jmh` itself
succeeded, so a failed or partial run never leaves behind a sidecar that looks freshly captured
next to a stale results file. The versioned CSVs back the technical documentation, so an
incomplete or mismatched export is an error, not a partial file to ignore.

`GET /api/v1/benchmarks` (TRD §6.6, fixed by TRD 1.3.10) serves these two versioned CSVs to
the frontend as-is — it never runs JMH and never recalculates anything. If either file is
missing or malformed, the server fails at startup naming the export command above, instead of
exposing a broken endpoint.

A fast, non-representative smoke run (shrinks the protocol; never commit its numbers) is
available by overriding the JMH Gradle plugin's properties and narrowing to a benchmark
subset with a regex:

```bash
./scripts/gradle-in-docker.sh :benchmarks:jmh -Pjmh.fork=1 -Pjmh.warmupIterations=1 -Pjmh.iterations=1 \
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

## Deployment (TRD §14.4, TAC-11)

**Decided by the author: backend on Render, frontend on Vercel.** Render runs this
repository's own image (this `Dockerfile`, built the way §14.2 describes) as a Docker web
service; `docker compose up` here stays the reproducibility path, not the acceptance test —
TAC-11 is the two public URLs below actually answering.

- **API base URL (Render):** _pending deployment — not yet assigned. This placeholder is
  replaced with the real Render URL once TAC-11's deployment step runs; it is never a
  fabricated URL._
- **Frontend URL (Vercel):** _pending deployment — set and documented by the frontend
  repository once TAC-11 runs there; linked here for convenience once known._

**Cold-start note.** Render's free tier suspends the service when idle; the first request
after a period of inactivity is slow while the instance wakes up. Before a demo, poll the
health endpoint until it answers and only then start the walkthrough:

```bash
docker run --rm alpine:3.20 sh -c 'apk add --no-cache curl >/dev/null && \
  until curl -sf "$1/actuator/health" | grep -q "\"status\":\"UP\""; do sleep 3; done; \
  echo "backend is warm"' _ https://<render-app>.onrender.com
```

CORS (TRD §14.4 point 3): `LEGAJO_CORS_ORIGINS` is set on Render to the exact public Vercel
origin; an unset or empty value only ever falls back to the two localhost defaults
(`http://localhost:5173`, `http://localhost`), never to a wildcard.
