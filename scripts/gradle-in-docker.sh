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
# - Does NOT mount the host's Docker socket by default — see the LEGAJO_DOCKER_SOCKET
#   section below.
#
# Usage: scripts/gradle-in-docker.sh <gradle args...>
#   scripts/gradle-in-docker.sh build
#   scripts/gradle-in-docker.sh jacocoRootReport
#   LEGAJO_DOCKER_SOCKET=1 scripts/gradle-in-docker.sh build   # opt in to Docker-socket access

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
BACKEND_DIR="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
# Kept in sync with the Dockerfile's own build-stage FROM line by hand: a Dockerfile and a
# POSIX shell script have no shared templating in this project, so this is the one other
# place the pinned Temurin JDK tag has to be repeated.
IMAGE="eclipse-temurin:25.0.4_7-jdk"
GRADLE_HOME_VOLUME="legajo-backend-gradle-home"

docker volume inspect "$GRADLE_HOME_VOLUME" >/dev/null 2>&1 \
    || docker volume create "$GRADLE_HOME_VOLUME" >/dev/null

# A Docker-managed named volume starts out root-owned; chown it once (as root, in its own
# short-lived container) to the invoking host uid/gid before Gradle — running as that same
# non-root uid below — tries to write into it.
docker run --rm -v "$GRADLE_HOME_VOLUME:/gradle-home" "$IMAGE" \
    chown -R "$(id -u):$(id -g)" /gradle-home

# Docker-socket access is opt-in, never the default (LEGAJO_DOCKER_SOCKET=1). Mounting the
# host's Docker socket hands the container root-equivalent control over the host's Docker
# daemon (anyone who can reach that socket can launch a privileged container and mount the
# host filesystem through it) — too much ambient authority to grant an ordinary `./gradlew
# build` by default. It also buys nothing today: verified for this task, the one GROBID
# adapter test in this repository (GrobidPdfMetadataExtractorTest) stubs GROBID with a plain
# JDK HttpServer, not Testcontainers, so `./gradlew build` does not need it. Set
# LEGAJO_DOCKER_SOCKET=1 the day a Testcontainers-backed integration test is actually added
# (AGENTS.md's testing table lists Testcontainers as that test's intended tool) — this then
# also adds a host-gateway alias so such a test can reach a sibling container back from
# inside this one.
DOCKER_SOCKET_ARGS=""
if [ "${LEGAJO_DOCKER_SOCKET:-0}" = "1" ]; then
    DOCKER_GID="$(getent group docker 2>/dev/null | cut -d: -f3 || true)"
    GROUP_ARGS=""
    if [ -n "$DOCKER_GID" ]; then
        GROUP_ARGS="--group-add $DOCKER_GID"
    fi
    DOCKER_SOCKET_ARGS="-v /var/run/docker.sock:/var/run/docker.sock --add-host=host.docker.internal:host-gateway -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal $GROUP_ARGS"
fi

# shellcheck disable=SC2086 # DOCKER_SOCKET_ARGS is intentionally word-split (empty, or several tokens)
exec docker run --rm \
    --user "$(id -u):$(id -g)" \
    -e HOME=/gradle-home \
    -e GRADLE_USER_HOME=/gradle-home \
    -v "$BACKEND_DIR:/workspace" \
    -v "$GRADLE_HOME_VOLUME:/gradle-home" \
    -w /workspace \
    $DOCKER_SOCKET_ARGS \
    "$IMAGE" \
    ./gradlew --no-daemon "$@"
