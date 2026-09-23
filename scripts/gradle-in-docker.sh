#!/bin/sh
# TRD §14.2: "verificación solo en contenedores" — no host JDK, no host mise, ever, for any
# check. Runs Gradle for this backend inside the same pinned Temurin 25 JDK image the
# Dockerfile's build stage uses, so a developer without a JDK installed gets exactly the
# build CI runs.
#
# - Bind-mounts the repository read-write (Gradle writes into build/ and .gradle/ as it
#   compiles and tests).
# - Uses a named volume for GRADLE_USER_HOME so downloaded dependencies and the Gradle
#   build cache survive across runs instead of being re-fetched every time.
# - Runs as the invoking host uid/gid so files Gradle writes into the bind-mounted repo are
#   owned by the developer, not root (constraint: no root-owned files left in the repo).
# - Mounts the host's Docker socket, with the socket's group added as a supplementary group
#   so the non-root container user can reach it, and adds a host-gateway alias, so a
#   Testcontainers-backed integration test can still launch a sibling container and be
#   reached back from inside this one (AGENTS.md's testing table lists Testcontainers as the
#   GROBID client's integration-test tool). As verified for this task, the one GROBID
#   adapter test in the repository today (GrobidPdfMetadataExtractorTest) does NOT use
#   Testcontainers — it stubs GROBID with a plain JDK HttpServer — so `./gradlew build`
#   does not currently need Docker-in-Docker access; this wiring is kept anyway so the
#   documented (AGENTS.md) Testcontainers-based integration test works the day it is added,
#   without a second pass over this script.
#
# Usage: scripts/gradle-in-docker.sh <gradle args...>
#   scripts/gradle-in-docker.sh build
#   scripts/gradle-in-docker.sh jacocoRootReport

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BACKEND_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
IMAGE="eclipse-temurin:25.0.4_7-jdk"
GRADLE_HOME_VOLUME="legajo-backend-gradle-home"

docker volume inspect "$GRADLE_HOME_VOLUME" >/dev/null 2>&1 \
    || docker volume create "$GRADLE_HOME_VOLUME" >/dev/null

# A Docker-managed named volume starts out root-owned; chown it once (as root, in its own
# short-lived container) to the invoking host uid/gid before Gradle — running as that same
# non-root uid below — tries to write into it.
docker run --rm -v "$GRADLE_HOME_VOLUME:/gradle-home" "$IMAGE" \
    chown -R "$(id -u):$(id -g)" /gradle-home

DOCKER_GID="$(getent group docker 2>/dev/null | cut -d: -f3 || true)"
GROUP_ARGS=""
if [ -n "$DOCKER_GID" ]; then
    GROUP_ARGS="--group-add $DOCKER_GID"
fi

# shellcheck disable=SC2086 # GROUP_ARGS is intentionally split (empty, or one --group-add token)
exec docker run --rm \
    --user "$(id -u):$(id -g)" \
    -e HOME=/gradle-home \
    -e GRADLE_USER_HOME=/gradle-home \
    -v "$BACKEND_DIR:/workspace" \
    -v "$GRADLE_HOME_VOLUME:/gradle-home" \
    -w /workspace \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --add-host=host.docker.internal:host-gateway \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    $GROUP_ARGS \
    "$IMAGE" \
    ./gradlew --no-daemon "$@"
