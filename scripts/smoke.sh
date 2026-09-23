#!/bin/sh
# TRD §14.2/§14.3: exercises the backend's documented smoke surface against a running
# instance — health, the published OpenAPI contract, the corpus listing, a real similarity
# comparison (needleman-wunsch), clustering (Ward), and a CORS preflight (TRD §14.4). Used
# the same way by a developer (README "Checks run in containers") and by CI
# (.github/workflows/backend.yml's image-smoke job), so "smoke" means the same six checks in
# both places.
#
# Usage: scripts/smoke.sh <base-url>
#
# One-command containerized run (TRD §14.2 "verificación solo en contenedores" — curl and
# jq come from the container, never the host):
#   docker run --rm --network host -v "$(pwd)/scripts:/scripts:ro" -w /scripts \
#     alpine:3.20 sh -c 'apk add --no-cache curl jq >/dev/null && ./smoke.sh "$1"' _ <base-url>

set -eu

BASE_URL="${1:?usage: smoke.sh <base-url>}"

# Bounded per-request timeouts (R3-curl-no-timeout/R4-smoke-no-timeouts): a hung TCP
# handshake or a stalled response must never block the smoke run forever.
CONNECT_TIMEOUT=5
MAX_TIME=15

# Bounded wait for the health check specifically: `docker compose up --wait` already blocks
# until the image's own HEALTHCHECK reports healthy, but this script is also runnable
# stand-alone against a container that has only just started (e.g. a plain `docker run -d`
# with no --wait), so it tolerates a service still coming up instead of failing on the very
# first, possibly-premature, request. 10 attempts x 2s = 20s bound, well inside the
# Dockerfile HEALTHCHECK's own start_period=40s.
HEALTH_WAIT_ATTEMPTS=10
HEALTH_WAIT_INTERVAL=2

fail() {
    echo "SMOKE FAILED: $1" >&2
    echo "$2" >&2
    exit 1
}

# Runs one HTTP request with the bounded timeouts above, fails clearly on a transport error
# or a non-200 status (both routed through fail(), never a bare curl/shell exit), and leaves
# the response body in REPLY_BODY for the caller to inspect further (R2-002: one shared
# helper instead of five copies of the same curl/status-split/fail dance).
# Usage: http_request <check-name> <curl-args...>
REPLY_BODY=""
http_request() {
    check_name="$1"
    shift
    response="$(curl -s --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" -w '\n%{http_code}' "$@")" \
        || fail "$check_name: request failed (connection error or timeout)" "$response"
    status="$(printf '%s' "$response" | tail -n1)"
    REPLY_BODY="$(printf '%s' "$response" | sed '$d')"
    [ "$status" = "200" ] || fail "$check_name: expected HTTP 200, got $status" "$REPLY_BODY"
}

# Same request/response shape as http_request, but never fails the whole run on a non-200 —
# the CORS check (5, below) needs to inspect a 403 response's headers, not treat it as a
# transport failure.
REPLY_STATUS=""
REPLY_HEADERS=""
cors_preflight() {
    origin="$1"
    # curl's own exit status must drive the failure check, so it is captured on its own
    # (not piped straight into tr below — a trailing pipe would report tr's exit status
    # instead, silently masking a connection failure under `set -eu` with no pipefail in
    # POSIX sh). Raw HTTP headers are CRLF-terminated; \r is stripped afterwards so a later
    # `grep '...$'` anchor matches the header value itself, not "right before the \r".
    raw_headers="$(curl -s -D - -o /dev/null --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" \
        -X OPTIONS "$BASE_URL/api/v1/corpus" \
        -H "Origin: $origin" -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: Content-Type')" \
        || fail "cors: preflight request failed (connection error or timeout)" "$raw_headers"
    REPLY_HEADERS="$(printf '%s' "$raw_headers" | tr -d '\r')"
}

echo "== smoke: $BASE_URL =="

# 1. Health (bounded wait for readiness — see HEALTH_WAIT_ATTEMPTS/INTERVAL above)
echo "-- GET /actuator/health"
attempt=1
health_status=""
health_body=""
while [ "$attempt" -le "$HEALTH_WAIT_ATTEMPTS" ]; do
    health_response="$(curl -s --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" -w '\n%{http_code}' "$BASE_URL/actuator/health" 2>/dev/null || true)"
    health_status="$(printf '%s' "$health_response" | tail -n1)"
    health_body="$(printf '%s' "$health_response" | sed '$d')"
    if [ "$health_status" = "200" ] && printf '%s' "$health_body" | grep -q '"status":"UP"'; then
        break
    fi
    attempt=$((attempt + 1))
    [ "$attempt" -le "$HEALTH_WAIT_ATTEMPTS" ] && sleep "$HEALTH_WAIT_INTERVAL"
done
[ "$health_status" = "200" ] \
    || fail "health: expected HTTP 200 after ${HEALTH_WAIT_ATTEMPTS}x${HEALTH_WAIT_INTERVAL}s of retries, got $health_status" "$health_body"
printf '%s' "$health_body" | grep -q '"status":"UP"' \
    || fail "health: expected status UP after ${HEALTH_WAIT_ATTEMPTS}x${HEALTH_WAIT_INTERVAL}s of retries" "$health_body"
echo "OK: health is UP"

# 2. OpenAPI contract (TRD §6.6: docs/openapi-legajo.yaml served as-is at this path)
echo "-- GET /openapi-legajo.yaml"
http_request "openapi" "$BASE_URL/openapi-legajo.yaml"
printf '%s' "$REPLY_BODY" | grep -q '^openapi:' \
    || fail "openapi: response does not look like an OpenAPI document (no 'openapi:' key)" "$REPLY_BODY"
echo "OK: openapi-legajo.yaml served"

# 3. Corpus listing (reference corpus, n = 20, TRD §6.1)
echo "-- GET /api/v1/corpus"
http_request "corpus" "$BASE_URL/api/v1/corpus"
corpus_body="$REPLY_BODY"
corpus_count="$(printf '%s' "$corpus_body" | jq 'length')" \
    || fail "corpus: could not parse the response as JSON (jq failed)" "$corpus_body"
[ "$corpus_count" = "20" ] || fail "corpus: expected 20 documents, got $corpus_count" "$corpus_body"
echo "OK: corpus has 20 documents"

# 4. Similarity compare (needleman-wunsch, d01 vs d02 — TAC-02/TAC-05)
echo "-- POST /api/v1/similarity/compare"
http_request "similarity/compare" -X POST "$BASE_URL/api/v1/similarity/compare" \
    -H 'Content-Type: application/json' \
    -d '{"documentIdA":"d01","documentIdB":"d02","algorithmIds":["needleman-wunsch"]}'
compare_body="$REPLY_BODY"
# TAC-05: normalizedScore must be a finite number in [0, 1]. jq -r prints the literal text
# "null" for a JSON null, which is non-empty — a plain `[ -n "$score" ]` presence check would
# wrongly pass a null score through undetected (R3-nw-score-null-passes). This instead
# classifies the value inside jq itself (present-and-numeric-and-in-range vs. not) and routes
# every non-OK case through fail() with a distinct message.
nw_result="$(printf '%s' "$compare_body" | jq -r '
    (.[] | select(.algorithmId == "needleman-wunsch") | .result.normalizedScore) as $score
    | if $score == null then "MISSING"
      elif ($score | type) != "number" then "NOT_A_NUMBER:\($score)"
      elif $score < 0 or $score > 1 then "OUT_OF_RANGE:\($score)"
      else "OK:\($score)"
      end
')" || fail "similarity/compare: could not parse the response as JSON (jq failed)" "$compare_body"
case "$nw_result" in
    OK:*)
        nw_score="${nw_result#OK:}"
        ;;
    ""|MISSING)
        fail "similarity/compare: no needleman-wunsch result in the response" "$compare_body"
        ;;
    NOT_A_NUMBER:*)
        fail "similarity/compare: needleman-wunsch normalizedScore is not a number (${nw_result#NOT_A_NUMBER:})" "$compare_body"
        ;;
    OUT_OF_RANGE:*)
        fail "similarity/compare: needleman-wunsch normalizedScore ${nw_result#OUT_OF_RANGE:} is outside [0, 1] (TAC-05)" "$compare_body"
        ;;
    *)
        fail "similarity/compare: unexpected validation result: $nw_result" "$compare_body"
        ;;
esac
echo "OK: needleman-wunsch(d01, d02) = $nw_score"

# 5. Clustering (Ward, 19 linkage rows over 20 documents — TAC-03)
echo "-- POST /api/v1/clustering"
http_request "clustering" -X POST "$BASE_URL/api/v1/clustering" \
    -H 'Content-Type: application/json' \
    -d '{"linkages":["ward"]}'
clustering_body="$REPLY_BODY"
rows_count="$(printf '%s' "$clustering_body" | jq '.[0].rows | length')" \
    || fail "clustering: could not parse the response as JSON (jq failed)" "$clustering_body"
[ "$rows_count" = "19" ] || fail "clustering: expected 19 linkage rows for ward, got $rows_count" "$clustering_body"
doc_count="$(printf '%s' "$clustering_body" | jq '.[0].documentIds | length')" \
    || fail "clustering: could not parse the response as JSON (jq failed)" "$clustering_body"
[ "$doc_count" = "20" ] || fail "clustering: expected 20 documentIds, got $doc_count" "$clustering_body"
echo "OK: ward linkage has 19 rows over 20 documents"

# 6. CORS preflight (TRD §14.4 point 3: an empty/absent LEGAJO_CORS_ORIGINS falls back to
# http://localhost:5173 and http://localhost — never to "allow every origin" — and a defined
# list replaces those defaults rather than adding to them; CorsWebConfiguration only ever
# registers the resolved, never-empty list, so an unlisted origin gets no CORS headers at all).
echo "-- OPTIONS /api/v1/corpus (CORS preflight)"
cors_preflight "http://localhost"
printf '%s' "$REPLY_HEADERS" | grep -qi '^Access-Control-Allow-Origin: http://localhost$' \
    || fail "cors: expected Access-Control-Allow-Origin: http://localhost for the default origin (TRD §14.4)" "$REPLY_HEADERS"
cors_preflight "http://smoke-test-unlisted-origin.invalid"
if printf '%s' "$REPLY_HEADERS" | grep -qi '^Access-Control-Allow-Origin:'; then
    fail "cors: an unlisted origin must not receive Access-Control-Allow-Origin" "$REPLY_HEADERS"
fi
echo "OK: CORS preflight allows the default http://localhost origin, rejects an unlisted one"

echo "== smoke: all checks passed =="
