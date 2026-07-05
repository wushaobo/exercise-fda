FROM eclipse-temurin:21.0.11_10-jdk AS builder

COPY common /app/common
COPY sync-facade /app/sync-facade

WORKDIR /app/sync-facade
RUN ./gradlew bootJar -x test --no-daemon -q

FROM eclipse-temurin:21.0.11_10-jre
COPY --from=builder /app/sync-facade/build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
