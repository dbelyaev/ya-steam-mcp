# syntax=docker/dockerfile:1

# Base images are pinned by digest; Dependabot (docker ecosystem) bumps them.
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS build
WORKDIR /build

# Layer 1: Maven wrapper + dependencies. Rebuilt only when pom.xml or the wrapper change.
# The repository lives inside the image (no cache mount) so the layer is reusable in CI cache exports.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp -Dmaven.repo.local=/build/.m2 dependency:go-offline

# Layer 2: compile + package (tests run in CI `verify`, not here), then split the fat jar into layers.
COPY src/main src/main
RUN ./mvnw -B -ntp -Dmaven.repo.local=/build/.m2 package -DskipTests -Djacoco.skip=true \
 && java -Djarmode=tools -jar target/ya-steam-mcp-*.jar extract --layers --destination /extracted \
 && mv /extracted/application/ya-steam-mcp-*.jar /extracted/application/application.jar


FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918 AS runtime

ARG VERSION=dev
LABEL org.opencontainers.image.title="ya-steam-mcp" \
      org.opencontainers.image.description="Read-only Steam MCP server over STDIO" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}"

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app

# Least to most frequently changing, so a code change only rebuilds the last COPY and the AOT layer.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
COPY docker/jvm.args docker/training-session.jsonl ./

# AOT cache (JDK 25): train with a real MCP session on stdin (offline: no Steam key means no network call).
# The sleep keeps stdin open until the last reply is written (EOF stops the server at once); the EOF then ends it
# cleanly, which writes the cache. timeout fails the build instead of hanging.
RUN (cat training-session.jsonl; sleep 3) | timeout 120 java @jvm.args -XX:AOTCacheOutput=app.aot -jar application.jar > /dev/null

USER app

# STDIO server: no EXPOSE, no HEALTHCHECK. Run with `docker run -i` (never -t) and pass secrets with -e at runtime.
ENTRYPOINT ["java", "@jvm.args", "-XX:AOTCache=app.aot", "-jar", "application.jar"]
