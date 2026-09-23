#!/bin/sh
# TRD §14.2/§14.3: exercises the backend's documented smoke surface against a running
# instance — health, the published OpenAPI contract, the corpus listing, a real similarity
# comparison (needleman-wunsch), and clustering (Ward). Used the same way by a developer
# (README "Checks run in containers") and by CI (.github/workflows/backend.yml's
# image-smoke job), so "smoke" means the same five checks in both places.
#
# Usage: scripts/smoke.sh <base-url>
#
# One-command containerized run (TRD §14.2 "verificación solo en contenedores" — curl and
# jq come from the container, never the host):
#   docker run --rm --network host -v "$(pwd)/scripts:/scripts:ro" -w /scripts \
#     alpine:3.20 sh -c 'apk add --no-cache curl jq >/dev/null && ./smoke.sh "$1"' _ <base-url>

set -eu

BASE_URL="${1:?usage: smoke.sh <base-url>}"

fail() {
    echo "SMOKE FAILED: $1" >&2
    echo "$2" >&2
    exit 1
}

echo "== smoke: $BASE_URL =="

# 1. Health
echo "-- GET /actuator/health"
health_response="$(curl -s -w '\n%{http_code}' "$BASE_URL/actuator/health")" \
    || fail "health: request failed (is the server reachable at $BASE_URL?)" "$health_response"
health_status="$(printf '%s' "$health_response" | tail -n1)"
health_body="$(printf '%s' "$health_response" | sed '$d')"
[ "$health_status" = "200" ] || fail "health: expected HTTP 200, got $health_status" "$health_body"
printf '%s' "$health_body" | grep -q '"status":"UP"' || fail "health: expected status UP" "$health_body"
echo "OK: health is UP"

# 2. OpenAPI contract (TRD §6.6: docs/openapi-legajo.yaml served as-is at this path)
echo "-- GET /openapi-legajo.yaml"
openapi_response="$(curl -s -w '\n%{http_code}' "$BASE_URL/openapi-legajo.yaml")" \
    || fail "openapi: request failed" "$openapi_response"
openapi_status="$(printf '%s' "$openapi_response" | tail -n1)"
openapi_body="$(printf '%s' "$openapi_response" | sed '$d')"
[ "$openapi_status" = "200" ] || fail "openapi: expected HTTP 200, got $openapi_status" "$openapi_body"
printf '%s' "$openapi_body" | grep -q '^openapi:' \
    || fail "openapi: response does not look like an OpenAPI document (no 'openapi:' key)" "$openapi_body"
echo "OK: openapi-legajo.yaml served"

# 3. Corpus listing (reference corpus, n = 20, TRD §6.1)
echo "-- GET /api/v1/corpus"
corpus_response="$(curl -s -w '\n%{http_code}' "$BASE_URL/api/v1/corpus")" \
    || fail "corpus: request failed" "$corpus_response"
corpus_status="$(printf '%s' "$corpus_response" | tail -n1)"
corpus_body="$(printf '%s' "$corpus_response" | sed '$d')"
[ "$corpus_status" = "200" ] || fail "corpus: expected HTTP 200, got $corpus_status" "$corpus_body"
corpus_count="$(printf '%s' "$corpus_body" | jq 'length')"
[ "$corpus_count" = "20" ] || fail "corpus: expected 20 documents, got $corpus_count" "$corpus_body"
echo "OK: corpus has 20 documents"

# 4. Similarity compare (needleman-wunsch, d01 vs d02 — TAC-02)
echo "-- POST /api/v1/similarity/compare"
compare_response="$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/v1/similarity/compare" \
    -H 'Content-Type: application/json' \
    -d '{"documentIdA":"d01","documentIdB":"d02","algorithmIds":["needleman-wunsch"]}')" \
    || fail "similarity/compare: request failed" "$compare_response"
compare_status="$(printf '%s' "$compare_response" | tail -n1)"
compare_body="$(printf '%s' "$compare_response" | sed '$d')"
[ "$compare_status" = "200" ] || fail "similarity/compare: expected HTTP 200, got $compare_status" "$compare_body"
nw_score="$(printf '%s' "$compare_body" | jq -r '.[] | select(.algorithmId == "needleman-wunsch") | .result.normalizedScore')"
[ -n "$nw_score" ] || fail "similarity/compare: no needleman-wunsch result in the response" "$compare_body"
echo "OK: needleman-wunsch(d01, d02) = $nw_score"

# 5. Clustering (Ward, 19 linkage rows over 20 documents — TAC-03)
echo "-- POST /api/v1/clustering"
clustering_response="$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/v1/clustering" \
    -H 'Content-Type: application/json' \
    -d '{"linkages":["ward"]}')" \
    || fail "clustering: request failed" "$clustering_response"
clustering_status="$(printf '%s' "$clustering_response" | tail -n1)"
clustering_body="$(printf '%s' "$clustering_response" | sed '$d')"
[ "$clustering_status" = "200" ] || fail "clustering: expected HTTP 200, got $clustering_status" "$clustering_body"
rows_count="$(printf '%s' "$clustering_body" | jq '.[0].rows | length')"
[ "$rows_count" = "19" ] || fail "clustering: expected 19 linkage rows for ward, got $rows_count" "$clustering_body"
doc_count="$(printf '%s' "$clustering_body" | jq '.[0].documentIds | length')"
[ "$doc_count" = "20" ] || fail "clustering: expected 20 documentIds, got $doc_count" "$clustering_body"
echo "OK: ward linkage has 19 rows over 20 documents"

echo "== smoke: all checks passed =="
