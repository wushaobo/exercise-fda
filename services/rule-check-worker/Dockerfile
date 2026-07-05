FROM eclipse-temurin:21.0.11_10-jdk AS builder

COPY common /app/common
COPY rule-check-worker /app/rule-check-worker

WORKDIR /app/rule-check-worker
RUN ./gradlew bootJar -x test --no-daemon -q

FROM eclipse-temurin:21.0.11_10-jre
COPY --from=builder /app/rule-check-worker/build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
