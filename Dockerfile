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

# Layer 3: minimal JRE. Modules are derived from the actual classpath, so a new dependency needs no edit here.
# jdk.unsupported (sun.misc.Unsafe, used by Caffeine/Netty-style libs), jdk.crypto.ec and jdk.localedata are
# added explicitly because jdeps cannot see reflective or service-loader use.
RUN MODULES="$(jdeps --ignore-missing-deps --multi-release 25 --print-module-deps \
      --class-path '/extracted/dependencies/lib/*' /extracted/application/application.jar)" \
 && jlink --add-modules "${MODULES},jdk.unsupported,jdk.crypto.ec,jdk.localedata" \
      --strip-debug --no-header-files --no-man-pages --compress zip-6 --output /jre \
 && /jre/bin/java --list-modules | tr '\n' ' '


FROM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS runtime

ARG VERSION=dev
LABEL org.opencontainers.image.title="ya-steam-mcp" \
      org.opencontainers.image.description="Read-only Steam MCP server over STDIO" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.version="${VERSION}"

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
ENV PATH="/opt/jre/bin:${PATH}"
WORKDIR /app

COPY --from=build /jre /opt/jre

# Least to most frequently changing, so a code change only rebuilds the last COPY.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
COPY docker/jvm.args ./

USER app

# STDIO server: no EXPOSE, no HEALTHCHECK. Run with `docker run -i` (never -t) and pass secrets with -e at runtime.
ENTRYPOINT ["java", "@jvm.args", "-jar", "application.jar"]
