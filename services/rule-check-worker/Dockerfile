FROM gradle:9.6.1-jdk21 AS builder
WORKDIR /app

COPY . .
RUN gradle bootJar -x test --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/build/libs/app.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
