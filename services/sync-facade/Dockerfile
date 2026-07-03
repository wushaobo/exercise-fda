# syntax=docker/dockerfile:1
FROM gradle:9.6.1-jdk21 AS builder
WORKDIR /app

COPY build.gradle settings.gradle ./
RUN mkdir -p /root/.gradle/init.d
RUN --mount=type=secret,id=gradle-init,dst=/root/.gradle/init.d/mirror.gradle \
    gradle dependencies --no-daemon -q 2>/dev/null || true

COPY src/ src/
RUN --mount=type=secret,id=gradle-init,dst=/root/.gradle/init.d/mirror.gradle \
    gradle bootJar -x test --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/build/libs/app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
