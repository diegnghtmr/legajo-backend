# syntax=docker/dockerfile:1.7
#
# TRD §14.2: multi-stage build on a pinned Temurin 25 JDK, running on the matching Temurin
# 25 JRE. Both stages pin the exact patch build (25.0.4_7) the team already develops and
# tests against (.mise.toml: temurin-25.0.4+7.0.LTS), so the image's JVM is never whatever
# "25" happens to resolve to on the day of the build.

FROM eclipse-temurin:25.0.4_7-jdk AS build
WORKDIR /workspace

# The whole (already .dockerignore-trimmed) build context, not a "copy build files first"
# partial-copy trick: this is a multi-module Gradle project with its own build.gradle.kts
# per subproject, so a partial copy would need to enumerate and keep every one of them in
# sync by hand. The BuildKit cache mount below gives the same caching benefit — the
# downloaded dependencies and Gradle's build cache survive across image builds — without
# that maintenance cost.
COPY . .
RUN chmod +x gradlew

# Tests run separately, never as part of the image build: CI's own build job already runs
# the full `./gradlew build`, and scripts/gradle-in-docker.sh gives developers the same
# containerized `./gradlew build` locally. Skipping test/check here keeps the image build
# fast and means a red test never blocks an otherwise-buildable jar from being inspected.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :bootstrap:bootJar -x test -x check --no-daemon

FROM eclipse-temurin:25.0.4_7-jre AS runtime

# Container-aware JVM defaults. The JVM already auto-detects cgroup memory/CPU limits
# (UseContainerSupport has been on by default since JDK 10), so this only tightens the heap
# headroom explicitly rather than relying purely on the JVM's own default (25% of the
# container limit) — reasonable on Render's free tier, where headroom for GC/off-heap
# matters more than maximizing heap size.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"

# TRD §14.2/§9: DomainConfiguration resolves data/corpus.json, data/embeddings-*.json and
# benchmarks/results/*.csv relative to the process's working directory (bootstrap's
# build.gradle.kts pins the same convention for `test`/`bootRun`), so the jar and both data
# directories live as siblings under one WORKDIR here too.
WORKDIR /app

RUN groupadd --system legajo \
    && useradd --system --gid legajo --home-dir /app --shell /usr/sbin/nologin legajo \
    && mkdir -p /app/data /app/benchmarks/results \
    && chown -R legajo:legajo /app

COPY --from=build --chown=legajo:legajo /workspace/bootstrap/build/libs/bootstrap-0.1.0-SNAPSHOT.jar /app/app.jar
# Only the three generated, versioned data files the server actually reads (TRD §6.1):
# never data/pdfs/ (git-ignored teacher PDFs, ingestion input only, not read at runtime) and
# never data/corpus-review.md (a manual-review aid, regenerated as needed, not read either).
COPY --from=build --chown=legajo:legajo \
    /workspace/data/corpus.json \
    /workspace/data/embeddings-minilm.json \
    /workspace/data/embeddings-openai.json \
    /app/data/
COPY --from=build --chown=legajo:legajo \
    /workspace/benchmarks/results/jmh-results.csv \
    /workspace/benchmarks/results/slopes.csv \
    /app/benchmarks/results/

USER legajo
EXPOSE 8080

# This Temurin JRE base has neither curl nor wget (it is an Ubuntu-minimal image), and
# installing one only for the healthcheck would grow the image for no other benefit; bash
# *is* present, so this reads the health endpoint over its /dev/tcp pseudo-device instead
# and checks both the HTTP status line and the JSON body, with no extra package installed.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD ["bash", "-c", "\
exec 3<>/dev/tcp/127.0.0.1/8080 && \
printf 'GET /actuator/health HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && \
response=$(cat <&3) && \
echo \"$response\" | head -n1 | grep -q ' 200 ' && \
echo \"$response\" | grep -q '\"status\":\"UP\"'"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
