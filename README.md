# Legajo backend

Server for Legajo: six hand-written text-similarity capabilities, four hierarchical
clustering linkages, and their internal metrics, exposed over a REST API.

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

## Conventions

How the code is kept, not just what it does.

| `domain` package | Owns |
|---|---|
| `similarity` | `levenshtein`, `needleman-wunsch`, `jaccard`, `tfidf-cosine`, `embedding-local`, `embedding-api` — one class per capability |
| `clustering` | The Lance-Williams merge engine and the four linkages: `SingleLinkage`, `CompleteLinkage`, `AverageLinkage`, `WardLinkage` |
| `evaluation` | Cophenetic correlation, mean silhouette, Davies-Bouldin |
| `preprocess` | NFC normalization, lowercasing, tokenization, English stopword removal, optional Porter stemming (off by default) |
| `corpus` | The corpus model (`Corpus`, `CorpusDocument`), SHA-256 hashing, `CorpusVerifier` |
| `port` | The embeddings port and the corpus port |

- **Hand-written algorithms.** Levenshtein, Needleman-Wunsch, Jaccard, TF-IDF and cosine,
  the embeddings similarity metric, text preprocessing, Lance-Williams, cophenetic
  correlation, mean silhouette, and Davies-Bouldin are all written by hand with basic
  language constructs — no library implements any of them. Delegable: PDF parsing, the
  embedding model's own inference, the web framework, JSON, caching, packaging.
- **Fixed constants.** Needleman-Wunsch scores +1 (match), -1 (mismatch), -1 (gap), never
  tunable. TF-IDF's `df` and `N` are always computed over the whole corpus. Ward's
  coefficients require the doubled distance base (`2·D`) the shared Lance-Williams merge
  engine feeds it, not something Ward computes on its own. Determinism is bit-for-bit: the
  DP matrix backtrace prefers diagonal, then up, then left on ties; clustering merges the
  lexicographically smallest `(idx1, idx2)` pair on ties.
- **Contract first.** `docs/openapi-legajo.yaml` changes before the implementation that
  serves it; the frontend regenerates its types from it, and CI fails on drift.
- **Generated data.** `data/corpus.json` and `data/embeddings-*.json` come only from
  ingestion and precompute, never hand-edited; each embeddings cache is bound to the
  corpus by `corpusSha256`, and startup fails if they no longer match.
- **Commits and tests.** Conventional Commits, no AI attribution. Tests are written
  before the code they cover.

## Verification runs in containers

**No check in this repository runs against a host JDK, host Gradle, or host `mise`
install.** Every build, test, and smoke check runs inside a container, always — a
developer machine only needs Docker. `.mise.toml` still pins `java =
"temurin-25.0.4+7.0.LTS"`/`gradle = "9.2.1"` and `settings.gradle.kts` still applies the
Foojay Toolchains Resolver, but both exist only so an IDE's language server has a local JDK
to point at for autocomplete/navigation — neither is a supported way to build or verify
this project. See "Checks run in containers" below for the container command for every
check, and `scripts/gradle-in-docker.sh` for the Gradle runner they share.

CI's `build` job runs `scripts/gradle-in-docker.sh` too — the exact same container-only path
described above, not a separate `actions/setup-java` install — so what CI actually exercises
is the documented developer path, not a parallel one that could quietly drift from it. CI's
`image-smoke` job builds and exercises the image itself, the same way described below.

## Running with Docker

Build the backend image directly:

```bash
docker build -t legajo-backend:local .
```

Run the full `default`-profile stack with Compose — both `backend` and `frontend` — from
this directory (`backend/`), with the frontend repository checked out as a sibling of this
one (`../frontend`, or any other checkout via `LEGAJO_FRONTEND_DIR`):

```bash
docker compose up -d --build --wait
docker compose down
```

This builds and starts both containers, waits for both healthchecks, publishes the frontend
at http://localhost and the API at http://localhost:8080. No CORS configuration is needed:
an unset/empty `LEGAJO_CORS_ORIGINS` falls back to `http://localhost` among its documented
defaults — exactly the frontend's Compose-published origin — so the two
containers talk to each other correctly with nothing to set.

Backend only, no frontend checkout needed (what CI's `image-smoke` job runs, since it has
no frontend checkout to build from):

```bash
docker compose up -d --build --wait backend
docker compose down
```

Compose profiles:

| Profile | Services | Purpose |
|---|---|---|
| `default` (no `--profile` flag) | `backend`, `frontend` | The demo stack; `LEGAJO_EMBEDDING_PROVIDER` defaults to `cached`, no network needed |
| `ingest` | + `grobid` | One-shot corpus extraction only (see "Ingestion" below); never part of the demo |

`LEGAJO_EMBEDDING_PROVIDER` and `LEGAJO_CORS_ORIGINS` pass straight through from the host
environment into the `backend` service; unset, they keep the documented defaults (`cached`,
and the two localhost CORS origins respectively). `frontend` reads three of its own, all
optional: `LEGAJO_FRONTEND_DIR` (its build context, default `../frontend`),
`VITE_API_BASE_URL` (a build arg baked into the compiled bundle, default
`http://localhost:8080` — this is the address the *browser*, not the container, reaches the
backend at, so it must stay a host-reachable URL, never a Compose service name), and
`LEGAJO_FRONTEND_PORT` (the host side of the port mapping only, default `80`; the container
itself always listens on `8080`, frontend/Dockerfile).

Health checks: `GET /actuator/health` for `backend`, `GET /` for `frontend` — each probed
directly by that service's own image `HEALTHCHECK` (this repository's `Dockerfile`, and
`frontend/Dockerfile` in the sibling checkout), which the Compose services inherit unchanged
(see each Dockerfile's own comments for why `docker-compose.yml` never redefines either).
`frontend` also declares `depends_on: backend: condition: service_healthy`, so it does not
even start until `backend` is already healthy; together with both inherited healthchecks,
`docker compose up --wait` only returns once the whole stack is actually serving traffic,
not merely running.

CI's `image-smoke` job (`.github/workflows/backend.yml`) always names only `backend` on the
command line, so it never builds or even resolves `frontend`'s build context — a missing
`../frontend` checkout there (the normal case: that CI has none) is harmless. See
`docker-compose.yml`'s own header comment for why `frontend` still counts as part of the
plain, no-flag `default` profile despite that.

## Checks run in containers

Every check below is the exact command a developer or CI runs — no host JDK, no host
`npm`/`mise`, ever.

| Check | Container command |
|---|---|
| Full build (compile, unit tests, ArchUnit) | `./scripts/gradle-in-docker.sh build` |
| Tests only | `./scripts/gradle-in-docker.sh test` |
| Aggregated coverage (JaCoCo; line coverage must exceed 85% in similarity, clustering, evaluation) | `./scripts/gradle-in-docker.sh jacocoRootReport` |
| Start locally, no network | `docker compose up -d --wait backend` |
| Full stack locally, no network | `docker compose up -d --build --wait` (needs `../frontend`, or `LEGAJO_FRONTEND_DIR`) |
| Image smoke test (health, OpenAPI, corpus, a real NW comparison, Ward clustering, CORS preflight) | see below |
| JMH performance curves + CSV export (must run on the reference machine — see "Benchmarks" below) | `./scripts/gradle-in-docker.sh :benchmarks:jmh :benchmarks:jmhExport` |

The image smoke test always tears the stack down, whether the smoke script passed or
failed — and it must do so even under `set -e` (the safe default for a script, and this
block is meant to be saved and run as one): a plain `cmd1; cmd2` sequence looks safe when
typed by hand, but under `set -e` a failing `cmd1` aborts the script *before* `cmd2` (the
teardown) ever runs, leaking a running container; and without `set -e`, a failed
`docker compose up --wait` would silently fall through into running the smoke script anyway
against a stack that never came up, masking the real failure behind a confusing, unrelated
smoke error. An `EXIT` trap avoids both: it always runs, on success, on a `set -e` abort, or
on an interrupt, and it explicitly re-exits with the status that triggered it, so the whole
script's own exit code still reflects whichever step actually failed.

The whole block is wrapped in a `( ... )` subshell so it is also safe to paste straight into
an interactive shell, not just saved and run as a script: `set -e` only takes effect inside
the subshell, so a failing step there cannot close the terminal session it was pasted into
the way a bare `set -e` in the current shell would (confirmed: pasting the un-wrapped form
into an interactive `bash` and forcing a failure terminated that shell outright, before its
own teardown trap or any later command could run; the `( ... )` form tears down, reports the
failure's exit status, and leaves the surrounding shell running). The subshell changes
nothing about exit-status propagation or teardown-on-failure: both still work exactly as the
paragraph above describes, now for the whole `( ... )` command as seen from outside it.

```bash
(
  set -e
  trap 'status=$?; docker compose down; exit $status' EXIT
  docker compose up -d --wait backend
  docker run --rm --network host -v "$(pwd)/scripts:/scripts:ro" -w /scripts \
      alpine:3.20 sh -c 'apk add --no-cache curl jq >/dev/null && ./smoke.sh "$1"' \
      _ http://localhost:8080
)
```

`scripts/gradle-in-docker.sh` runs the same pinned Temurin 25 JDK image the Dockerfile's
build stage uses, with the repository bind-mounted and a named volume for the Gradle cache.
It does **not** mount the host's Docker socket by default — no test in this repository needs
it today (see the script's own comments) and it would otherwise grant the container
root-equivalent control over the host's Docker daemon for no benefit. Set
`LEGAJO_DOCKER_SOCKET=1` before the command (e.g. `LEGAJO_DOCKER_SOCKET=1
./scripts/gradle-in-docker.sh build`) to opt in, the day a Testcontainers-backed adapter
test is actually added.

## Environment variables

`.env.example` is the declared list of variables this backend reads; copy it to `.env`
for local runs and fill in the values there (`.env` is git-ignored, values never go in
git — use the hosting provider's secret panel in production).

| Variable | Read by | Required | Notes |
|---|---|---|---|
| `LEGAJO_EMBEDDING_PROVIDER` | `application.yml` (`legajo.embedding-provider`) | No, defaults to `cached` | `cached` reads `data/embeddings-*.json`, no network needed |
| `LEGAJO_CORS_ORIGINS` | `application.yml` (`legajo.cors-origins`) | No, defaults to `http://localhost:5173` and `http://localhost` | Comma-separated browser origins allowed by CORS for `/api/v1/**`; a defined list replaces the defaults instead of adding to them |
| `LEGAJO_PREPROCESS_STEMMING` | `application.yml` (`legajo.preprocess.stemming`) | No, defaults to `false` | `true` applies Porter stemming to the classic algorithms and `tfidf-cosine`; the embeddings are unaffected. The value in force is reported as `stemming` in every similarity trace, compare and matrix result, and clustering response |
| `SPRING_AI_OPENAI_BASE_URL` | `PrecomputeApiEmbeddingsCli`; the server (`DomainConfiguration`) when `LEGAJO_EMBEDDING_PROVIDER=live` | For `:bootstrap:precomputeApiEmbeddings` and for `live` mode | OpenAI-compatible embeddings endpoint (Gemini) |
| `SPRING_AI_OPENAI_API_KEY` | `PrecomputeApiEmbeddingsCli`; the server (`DomainConfiguration`) when `LEGAJO_EMBEDDING_PROVIDER=live` | For `:bootstrap:precomputeApiEmbeddings` and for `live` mode | Read from the environment only; never logged or included in an exception message |
| `LEGAJO_EMBEDDING_API_MODEL` | `PrecomputeApiEmbeddingsCli`; the server (`DomainConfiguration`) when `LEGAJO_EMBEDDING_PROVIDER=live` | Required by `:bootstrap:precomputeApiEmbeddings`; the server defaults to `gemini-embedding-2-preview` | Embedding model name |
| `LEGAJO_EMBEDDING_API_DIMENSION` | `PrecomputeApiEmbeddingsCli`; the server (`DomainConfiguration`) when `LEGAJO_EMBEDDING_PROVIDER=live` | Required by `:bootstrap:precomputeApiEmbeddings`; the server defaults to `1536` | Must be a positive integer |
| `LEGAJO_GROBID_URL` | `IngestCli` | Only for `:bootstrap:ingest` | GROBID endpoint for ingestion. Resolves as `--grobid-url`, then this variable, then `http://localhost:8070`; a blank value counts as unset |
| `SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH` | `PrecomputeApiEmbeddingsCli` | No | Optional, warn-only: Spring AI 2.0.x has no override point to route it to, so this CLI only warns if it is set to something other than `/embeddings`; see the class's Javadoc for the full history |

## Ingestion, validation and verification

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
   # wait until this returns "true" (containerized, no host curl):
   docker run --rm --network host alpine:3.20 sh -c 'apk add --no-cache curl >/dev/null && curl -s http://localhost:8070/api/isalive'
   ```

2. **Ingest.** Scans `--input` for `*.pdf` sorted by name, extracts each through GROBID
   with a PDFBox fallback (used automatically on a GROBID failure *or* an empty
   abstract), applies the ingestion cleaning step, and writes `--output` with every
   document `manuallyValidated=false`. `IngestCli` resolves the GROBID endpoint from
   `--grobid-url` (`LEGAJO_GROBID_URL`, then `http://localhost:8070`), and
   `http://localhost:8070` only reaches step 1's `grobid` container when the ingest itself
   also runs with host networking — otherwise "localhost" inside the ingest container is
   its own loopback, not the host's, and the Compose-published port is unreachable.
   `scripts/gradle-in-docker.sh` does not use host networking by default (least privilege —
   see the script's own comments), so this one step opts in explicitly:

   ```bash
   LEGAJO_NETWORK_HOST=1 ./scripts/gradle-in-docker.sh :bootstrap:ingest --args="--input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070"
   ```

3. **Review each abstract by hand.** This is the only mandatory control
   and is never automatic — inspect `title`/`authors`/`abstract` per document (e.g.
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

Re-running step 2 replaces `data/corpus.json` outright: every document goes
back to `manuallyValidated=false`, and any previously computed `data/embeddings-*.json`
caches become stale (their `corpusSha256` no longer matches) until embeddings are
recomputed for the new corpus.

## Benchmarks

`benchmarks/` measures the algorithms with JMH under a fixed protocol, the same on every
benchmark class: `@BenchmarkMode(AverageTime)`, `@Fork(1)`, `@Warmup(iterations = 3, time = 1)`,
`@Measurement(iterations = 5, time = 1)`.

| Family | Classes | What varies |
|---|---|---|
| Pairwise classic curves | `pairwise.LevenshteinBenchmark`, `NeedlemanWunschBenchmark`, `JaccardBenchmark`, `TfIdfCosineBenchmark` | Token-sequence length L ∈ {50, 100, 200, 400, 800}, built from real corpus tokens |
| HAC curves | `hac.LanceWilliamsBenchmark` (× 4 linkage criteria), `hac.InternalMetricsBenchmark` (mean silhouette, Davies-Bouldin) | n ∈ {5, 10, 20, 40, 80} synthetic unit vectors |
| Embedding primitive | `embedding.EmbeddingPrimitiveBenchmark` | d ∈ {384, 1536}, one measurement each, no curve |
| SLO benchmarks | `slo.ClassicPairwiseSloBenchmark` (per classic algorithm), `slo.ClusteringSloBenchmark` (all four linkages) | Fixed at the real reference corpus, n = 20, similarity cache never involved |

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
  log-log slope of each curve next to its theoretical complexity — O(L²) for
  `levenshtein`/`needleman-wunsch`; O(L) per pair for `jaccard`/`tfidf-cosine` (TF-IDF's
  one-time corpus indexing is not part of the measured operation); O(n³) for the
  Lance-Williams `hac` engine, shared by all four linkages; O(n²) per k for
  `mean-silhouette`; O(n·d) per k for `davies-bouldin` (the dominant term at the fixed
  small k and d used here); and O(d) for both embedding primitives. A fixed-n SLO family
  has no theoretical exponent and is excluded.

The export is strict: it writes neither CSV if any benchmark result cannot be classified into
a family (every skipped record is listed, with its reason, in the failure), if the
`build/results/jmh/harness.properties` sidecar `jmh` writes is missing or bound (by SHA-256) to
a different JMH results file than the one being exported, or if the JDK JMH itself reports
disagrees with that sidecar. `jmh`'s own sidecar-capturing step only runs when `jmh` itself
succeeded, so a failed or partial run never leaves behind a sidecar that looks freshly captured
next to a stale results file. The versioned CSVs back the technical documentation, so an
incomplete or mismatched export is an error, not a partial file to ignore.

`GET /api/v1/benchmarks` serves these two versioned CSVs to
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

The reference harness is the machine recorded in `jmh-results.csv`'s header; the
baseline target is a 4 vCPU / 8 GB x86-64 machine. `.github/workflows/benchmarks.yml` runs the
JMH suite manually (`workflow_dispatch`) or on a `bench-*` tag push, never on every push or
pull request, and uploads `jmh-results.json` and the two CSVs as a build artifact.
**The numbers that job produces are not the reference harness**: it runs on a shared,
unpinned GitHub-hosted runner, not the documented machine. The versioned CSVs in
`benchmarks/results/` — the reference numbers the technical documentation cites — always
come from a local run on the reference harness, never from CI.

## Deployment

**Decided by the author: backend on Render, frontend on Vercel.** Render runs this
repository's own image (this `Dockerfile`) as a Docker web
service; `docker compose up` here stays the reproducibility path, not the acceptance test —
what proves deployment is the two public URLs below actually answering.

- **API base URL (Render):** https://legajo-backend.onrender.com
- **Frontend URL (Vercel):** https://legajo-frontend.vercel.app

### Native Git deployment policy

The existing `legajo-backend` Docker web service uses Render's native Git connection
to `github.com/diegnghtmr/legajo-backend` with these dashboard settings:

- **Branch:** `main` — automatic Git deployments apply only to this branch.
- **Auto-Deploy:** `After CI Checks Pass` — automatic deployments wait for CI checks to pass.
- **PR Previews:** `Off` — no automatic pull-request preview services are created.

This policy governs automatic Git deployments; it does not prohibit manual deployments.

**Cold-start note.** Render's free tier suspends the service when idle; the first request
after a period of inactivity is slow while the instance wakes up. Before a demo, poll the
health endpoint until it answers and only then start the walkthrough:

```bash
docker run --rm alpine:3.20 sh -c 'apk add --no-cache curl >/dev/null && \
  until curl -sf "$1/actuator/health" | grep -q "\"status\":\"UP\""; do sleep 3; done; \
  echo "backend is warm"' _ https://legajo-backend.onrender.com
```

CORS: `LEGAJO_CORS_ORIGINS` is set on Render to the exact public Vercel
origin; an unset or empty value only ever falls back to the two localhost defaults
(`http://localhost:5173`, `http://localhost`), never to a wildcard.
