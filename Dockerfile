# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean bootJar -x test --no-daemon \
    && JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/app.jar


FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system app \
    && useradd \
        --system \
        --gid app \
        --home-dir /app \
        --create-home \
        app

WORKDIR /app

COPY --from=builder --chown=app:app /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
