# syntax=docker/dockerfile:1

# ---- Build stage: compile and extract the Spring Boot layers ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -ntp package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/tesoro-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S tesoro && adduser -S -G tesoro -u 10001 tesoro
WORKDIR /app
# Layers ordered from least to most frequently changing, for better image caching
COPY --from=build --chown=tesoro:tesoro /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=tesoro:tesoro /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=tesoro:tesoro /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=tesoro:tesoro /workspace/target/extracted/application/ ./
USER 10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness > /dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
