# Legajo backend reference

## Modules and maintenance conventions

Root package: `co.edu.uniquindio.legajo`.

| Module | Dependencies | Owns |
| --- | --- | --- |
| `domain` | No project dependencies | Hand-written similarity, preprocessing, clustering, metrics, corpus model, and ports. No Spring, Jackson, or DJL. |
| `application` | `domain` | Use cases and algorithm orchestration. |
| `infrastructure` | `application`, `domain` | REST, PDF extraction, embedding adapters, corpus JSON, and caching. |
| `bootstrap` | `domain`, `application`, `infrastructure` | Spring composition root, startup, and the corpus command-line tools. |
| `benchmarks` | `domain` | JMH harness, CSV export, and reference results. |

Within `domain`, `similarity` separates the six capabilities; `clustering` contains the shared Lance–Williams engine and the single, complete, average, and Ward linkage implementations; `evaluation` owns cophenetic correlation, mean silhouette, and Davies–Bouldin. `preprocess` handles NFC normalization, lowercasing, tokenization, English stopwords, and optional Porter stemming (off by default). `corpus` defines corpus/document records, SHA-256 hashing, and verification; `port` defines corpus and embeddings interfaces. The embedding similarity metric is handwritten; external libraries handle model inference, PDF parsing, JSON, and caching, not the similarity scores or clusters.

Needleman–Wunsch has fixed scores (`+1` match, `-1` mismatch, `-1` gap) and breaks backtrace ties diagonal, up, then left. TF-IDF gets `df` and `N` from the full corpus. The shared engine supplies the doubled distance base for Ward and chooses the lexicographically smallest index pair on merge ties. Update [the OpenAPI contract](openapi-legajo.yaml) before changing the implementation; the sibling client regenerates its types from a copy. Make changes with tests first and use Conventional Commits.

## Running and verifying in containers

The developer build and tests use Docker, not a host JDK, Gradle, or `mise`. The host pins and toolchain resolver are useful for IDE language tooling. The [Gradle container runner](../scripts/gradle-in-docker.sh) uses the same pinned Temurin 25 JDK image as the Dockerfile build stage, bind-mounts the checkout, and stores Gradle dependencies in a named volume. The host Docker socket is not mounted unless `LEGAJO_DOCKER_SOCKET=1` is explicitly set; the current suite does not need it. CI uses the same container runner.

| Check | Command |
| --- | --- |
| Compile, tests, and ArchUnit | `./scripts/gradle-in-docker.sh build` |
| Tests only | `./scripts/gradle-in-docker.sh test` |
| Aggregate JaCoCo report | `./scripts/gradle-in-docker.sh jacocoRootReport` |
| Start a previously built API image | `docker compose up -d --wait backend` |
| Build and start the API only | `docker compose up -d --build --wait backend` |
| Build and start both services | `docker compose up -d --build --wait` |
| Benchmark and export on the reference machine | `./scripts/gradle-in-docker.sh :benchmarks:jmh :benchmarks:jmhExport` |

The full stack needs a sibling checkout at `../frontend`, or `LEGAJO_FRONTEND_DIR`. The frontend waits for the API's inherited image healthcheck; `--wait` checks both `/actuator/health` and the frontend's `/`. The API-only target does not build or resolve the frontend context. The default stack uses cached vectors, with no external embedding call; a cold Docker build can still need network access to fetch images or dependencies. The `ingest` profile adds GROBID only for offline extraction.

For a disposable API-only stack, the [CI image smoke job](../.github/workflows/backend.yml) builds the image and runs the following check in a separate Alpine container. Keep the cleanup trap in a subshell so Compose shuts down even if startup or the probe fails, without exiting the shell from which the block was pasted:

```sh
(
  set -e
  trap 'status=$?; docker compose down; exit $status' EXIT
  docker compose up -d --build --wait backend
  docker run --rm --network host -v "$PWD/scripts:/scripts:ro" -w /scripts \
    alpine:3.20 sh -c 'apk add --no-cache curl jq >/dev/null && ./smoke.sh "$1"' \
    _ http://localhost:8080
)
```

The probe covers health, OpenAPI, corpus access, a Needleman–Wunsch comparison, Ward clustering, and CORS preflight. Report generation and the 85% domain line coverage threshold are distinct: the domain build checks the threshold for similarity, clustering, and evaluation.

## Ingestion, validation, and verification

The demo uses the checked-in corpus and matching vectors; PDF ingestion is a separate workflow. The reference PDFs are not checked in. Ingestion enumerates `*.pdf` by filename, extracts with GROBID, falls back to PDFBox if GROBID fails or yields an empty abstract, cleans the text, and sets `manuallyValidated=false` on each generated document. The Gradle entry points are plain `main` classes, not Spring application profiles.

1. From the backend root run `docker compose --profile ingest up -d grobid`. Wait for `http://localhost:8070/api/isalive` to return `true`.
2. Run `LEGAJO_NETWORK_HOST=1 ./scripts/gradle-in-docker.sh :bootstrap:ingest --args='--input=data/pdfs --output=data/corpus.json --grobid-url=http://localhost:8070'`. Host networking is needed to reach the published port at localhost. The explicit flag overrides `LEGAJO_GROBID_URL`, which falls back to that same local URL when unset or blank.
3. Check each extracted title, author list, and abstract manually. Run `./scripts/gradle-in-docker.sh :bootstrap:validateCorpus --args='--ids=d01,d02'` for the reviewed IDs, or use `--all` only after checking every document. Validation freezes each `abstractSha256` and recomputes `corpusSha256`.
4. Run `./scripts/gradle-in-docker.sh :bootstrap:verifyCorpus`. It lists all violations and exits nonzero on invalid data. The reference corpus has `sourceCount=20`, 20 documents, and no unreviewed entries.
5. Regenerate matching vector caches before using cached mode. The tasks are `:bootstrap:precomputeEmbeddings` (local MiniLM) and `:bootstrap:precomputeApiEmbeddings` (OpenAI-compatible Gemini). The latter requires the provider URL, API key, model, and dimension listed in the [README](../README.md#environment-variables). `SPRING_AI_OPENAI_EMBEDDING_EMBEDDINGS_PATH` is warn-only for this task; Spring AI does not route to a custom path here.
6. Stop the extractor with `docker compose --profile ingest down` when that disposable Compose project is no longer needed.

Re-ingesting replaces `data/corpus.json` and resets manual validation. Do not hand-edit generated `data/corpus.json` or `data/embeddings-*.json`; the caches are bound to the corpus hash and startup fails if they do not match.

## Benchmark protocol and formats

Each JMH family uses average time, one fork, three one-second warmup iterations, and five one-second measurements. Run JMH and CSV export in one Gradle session on the reference machine; the harness records the machine used for `jmh`, not for `jmhExport`.

| Family | What varies | Theoretical complexity used in export |
| --- | --- | --- |
| Levenshtein and Needleman–Wunsch | Token sequence length `L` of 50, 100, 200, 400, 800 | `O(L²)` |
| Jaccard and TF-IDF cosine | Same token lengths; one-time TF-IDF corpus indexing is not timed | `O(L)` per pair |
| Lance–Williams with four linkages | `n` of 5, 10, 20, 40, 80 synthetic unit vectors | `O(n³)` |
| Mean silhouette and Davies–Bouldin | Same vector counts | `O(n²)` per `k`, and `O(n·d)` for Davies–Bouldin at fixed small `k` |
| Embedding primitive | Dimension `d` of 384 or 1536; a measurement at each dimension, not a complexity curve | Not fitted |
| Classic pairwise and clustering SLO | Fixed 20-document corpus, with the similarity cache bypassed | Not fitted |

[jmh-results.csv](../benchmarks/results/jmh-results.csv) records the harness (CPU, logical cores, RAM, JDK, OS, UTC date) and `benchmark,family,parameter,size,score,error,unit`. [slopes.csv](../benchmarks/results/slopes.csv) records `family,points,empiricalSlope,theoreticalExponent`; the fit uses least squares on log-transformed points. The reference target is a 4 vCPU / 8 GB x86-64 machine; use the checked-in CSV header to identify the actual reference run. The export rejects unclassified results, missing or hash-mismatched `build/results/jmh/harness.properties`, and JDK disagreement between JMH and the sidecar. It writes neither CSV on a rejected run. The API serves the CSVs as-is and refuses to start with missing or malformed benchmark files. The [benchmark workflow](../.github/workflows/benchmarks.yml) uploads JSON and both CSVs when dispatched or run on a `bench-*` tag; its shared runner does not produce the reference numbers.

## Native Git deployment policy

The deployment record documents a Render Docker web service connected to `diegnghtmr/legajo-backend`. The intended dashboard configuration is `main`, **Auto-Deploy: After CI Checks Pass**, and **PR Previews: Off**; manual deployments are still possible. These are service settings, not enforced by the repository. The Render service publishes the API; the Vercel app origin is passed as `LEGAJO_CORS_ORIGINS`. If left empty, the allow-list falls back to the two local origins, not a wildcard.

The free-tier API may sleep after inactivity. Before a walkthrough, wait for `https://legajo-backend.onrender.com/actuator/health` to return HTTP `200` with `UP`; a fresh Compose deployment checks local reproducibility, not the public endpoint. The dashboard settings and hosted endpoint were not rechecked in this documentation pass.
