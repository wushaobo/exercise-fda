FROM eclipse-temurin:21.0.11_10-jdk AS builder
WORKDIR /app

COPY . .
RUN ./gradlew bootJar -x test --no-daemon -q

FROM eclipse-temurin:21.0.11_10-jre
COPY --from=builder /app/build/libs/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
