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
#
# Environment overrides (see each variable's own comment below for the full reasoning):
#   SMOKE_READY_TIMEOUT_SECONDS  Wall-clock deadline for the health-readiness wait (default 120).
#   SMOKE_CORS_ALLOWED_ORIGIN    Origin the CORS check expects to be allowed (default http://localhost).

set -eu

BASE_URL="${1:?usage: smoke.sh <base-url>}"

# Bounded per-request timeouts: a hung TCP handshake or a stalled response must never block
# the smoke run forever.
CONNECT_TIMEOUT=5
MAX_TIME=15

# Bounded *wall-clock deadline* for the health-readiness wait, not an attempt count: an
# attempt count times a fixed sleep interval silently understates the real bound, because it
# ignores each attempt's own request time (up to MAX_TIME above) — this computes an actual
# deadline with `date +%s` and checks elapsed time against it instead.
# `docker compose up --wait` already blocks until the image's own HEALTHCHECK reports
# healthy, but this script is also runnable stand-alone against a container that has only
# just started (e.g. a plain `docker run -d` with no --wait), so it tolerates a service still
# coming up instead of failing on the very first, possibly-premature, request. Default 120s:
# comfortably above the Dockerfile HEALTHCHECK's own worst case before Docker itself would
# give up (start_period=40s + retries=5 x interval=10s = 90s) plus margin for a genuinely
# slow JVM cold start (e.g. Render's free tier suspending when idle, TRD §14.4 point 4) —
# override with SMOKE_READY_TIMEOUT_SECONDS for an even slower environment.
SMOKE_READY_TIMEOUT_SECONDS="${SMOKE_READY_TIMEOUT_SECONDS:-120}"
HEALTH_WAIT_INTERVAL=2

# The CORS preflight check's expected allowed origin (TRD §14.4): defaults to one of the two
# fallback origins a backend started with an unset/empty LEGAJO_CORS_ORIGINS falls back to.
# A backend started with a non-default LEGAJO_CORS_ORIGINS (e.g. a Render deployment
# configured with the Vercel origin) must pass its own origin here, or this check fails
# against a correctly-configured backend for the wrong reason.
SMOKE_CORS_ALLOWED_ORIGIN="${SMOKE_CORS_ALLOWED_ORIGIN:-http://localhost}"

fail() {
    echo "SMOKE FAILED: $1" >&2
    echo "$2" >&2
    exit 1
}

# Runs one HTTP request with the bounded timeouts above, fails clearly on a transport error
# or a non-200 status (both routed through fail(), never a bare curl/shell exit), and leaves
# the response body in REPLY_BODY for the caller to inspect further — one shared helper
# instead of a separate copy of the same curl/status-split/fail dance per check.
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

# Same request/response shape as http_request, but deliberately does not treat a non-200
# (e.g. Spring's 403 response to a disallowed preflight origin) as a transport failure — the
# CORS check (6, below) needs to inspect which headers came back for both an allowed and a
# disallowed origin, not have a non-200 short-circuit the run before it can look.
# Usage: cors_preflight <origin>
REPLY_HEADERS=""
cors_preflight() {
    origin="$1"
    # curl's own exit status must drive the failure check, so it is captured on its own
    # (not piped straight into tr below — a trailing pipe would report tr's exit status
    # instead, silently masking a connection failure under `set -eu` with no pipefail in
    # POSIX sh). Raw HTTP headers are CRLF-terminated; \r is stripped afterwards so the later
    # exact-value comparison isn't thrown off by a trailing \r.
    raw_headers="$(curl -s -D - -o /dev/null --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" \
        -X OPTIONS "$BASE_URL/api/v1/corpus" \
        -H "Origin: $origin" -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: Content-Type')" \
        || fail "cors: preflight request failed (connection error or timeout)" "$raw_headers"
    REPLY_HEADERS="$(printf '%s' "$raw_headers" | tr -d '\r')"
}

echo "== smoke: $BASE_URL =="

# 1. Health (bounded wall-clock wait for readiness — see SMOKE_READY_TIMEOUT_SECONDS above)
echo "-- GET /actuator/health"
health_deadline=$(( $(date +%s) + SMOKE_READY_TIMEOUT_SECONDS ))
health_status=""
health_body=""
while :; do
    health_response="$(curl -s --connect-timeout "$CONNECT_TIMEOUT" --max-time "$MAX_TIME" -w '\n%{http_code}' "$BASE_URL/actuator/health" 2>/dev/null || true)"
    health_status="$(printf '%s' "$health_response" | tail -n1)"
    health_body="$(printf '%s' "$health_response" | sed '$d')"
    if [ "$health_status" = "200" ] && printf '%s' "$health_body" | grep -q '"status":"UP"'; then
        break
    fi
    [ "$(date +%s)" -ge "$health_deadline" ] && break
    sleep "$HEALTH_WAIT_INTERVAL"
done
[ "$health_status" = "200" ] \
    || fail "health: expected HTTP 200 within ${SMOKE_READY_TIMEOUT_SECONDS}s, got $health_status" "$health_body"
printf '%s' "$health_body" | grep -q '"status":"UP"' \
    || fail "health: expected status UP within ${SMOKE_READY_TIMEOUT_SECONDS}s" "$health_body"
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
# TAC-05: normalizedScore must be a finite number in [0, 1], and exactly one
# needleman-wunsch result must be present. jq -r prints the literal text "null" for a JSON
# null, which is non-empty — a plain `[ -n "$score" ]` presence check would wrongly pass a
# null score through undetected. Collecting every match into an array first (instead of a
# bare `select | as $score` pipeline, which would silently re-run the classification once
# per match and only ever look at the last output line) also catches a second,
# contract-violating match instead of letting it mask the first one. Every case is
# classified inside jq itself and routed through fail() with a distinct message.
nw_result="$(printf '%s' "$compare_body" | jq -r '
    [.[] | select(.algorithmId == "needleman-wunsch")] as $matches
    | if ($matches | length) == 0 then "MISSING"
      elif ($matches | length) > 1 then "MULTIPLE:\($matches | length)"
      else ($matches[0].result.normalizedScore) as $score
        | if $score == null then "MISSING"
          elif ($score | type) != "number" then "NOT_A_NUMBER:\($score)"
          elif $score < 0 or $score > 1 then "OUT_OF_RANGE:\($score)"
          else "OK:\($score)"
          end
      end
')" || fail "similarity/compare: could not parse the response as JSON (jq failed)" "$compare_body"
case "$nw_result" in
    OK:*)
        nw_score="${nw_result#OK:}"
        ;;
    ""|MISSING)
        fail "similarity/compare: no needleman-wunsch result in the response" "$compare_body"
        ;;
    MULTIPLE:*)
        fail "similarity/compare: expected exactly one needleman-wunsch result, got ${nw_result#MULTIPLE:}" "$compare_body"
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
# registers the resolved, never-empty list, so an unlisted origin gets no CORS headers at
# all). Checks against SMOKE_CORS_ALLOWED_ORIGIN, not a hardcoded default, so this passes
# against any correctly-configured deployment, not only the local Compose default.
echo "-- OPTIONS /api/v1/corpus (CORS preflight, expecting $SMOKE_CORS_ALLOWED_ORIGIN to be allowed)"
cors_preflight "$SMOKE_CORS_ALLOWED_ORIGIN"
# Extracted and compared as a plain string, not matched by a regex built from
# SMOKE_CORS_ALLOWED_ORIGIN, so a "." or any other regex metacharacter in the configured
# origin can never change what this check actually matches.
allowed_origin_header="$(printf '%s' "$REPLY_HEADERS" | grep -i '^Access-Control-Allow-Origin:' | sed 's/^[Aa]ccess-[Cc]ontrol-[Aa]llow-[Oo]rigin: *//')"
[ "$allowed_origin_header" = "$SMOKE_CORS_ALLOWED_ORIGIN" ] \
    || fail "cors: expected Access-Control-Allow-Origin: $SMOKE_CORS_ALLOWED_ORIGIN, got '${allowed_origin_header:-<absent>}' (TRD §14.4; override with SMOKE_CORS_ALLOWED_ORIGIN if this backend's LEGAJO_CORS_ORIGINS differs from the default)" "$REPLY_HEADERS"
cors_preflight "http://smoke-test-unlisted-origin.invalid"
if printf '%s' "$REPLY_HEADERS" | grep -qi '^Access-Control-Allow-Origin:'; then
    fail "cors: an unlisted origin must not receive Access-Control-Allow-Origin" "$REPLY_HEADERS"
fi
echo "OK: CORS preflight allows $SMOKE_CORS_ALLOWED_ORIGIN, rejects an unlisted origin"

echo "== smoke: all checks passed =="
