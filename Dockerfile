FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw dependency:go-offline -B

COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S taskbot && adduser -S taskbot -G taskbot

WORKDIR /app
COPY --from=builder app/target/*.jar app.jar

RUN chown -R taskbot:taskbot /app

USER taskbot

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:ZCollectionInterval=30", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
