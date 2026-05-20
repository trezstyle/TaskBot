FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B -q

COPY src src
RUN mvn package -DskipTests -B -q

# --- Runtime with ffmpeg + python3 + vosk ---
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install ffmpeg, python3, pip, venv, curl, unzip
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg python3 python3-pip python3-venv curl unzip && \
    rm -rf /var/lib/apt/lists/*

# Install vosk Python package in venv
RUN python3 -m venv /opt/vosk-venv && \
    /opt/vosk-venv/bin/pip install --upgrade pip && \
    /opt/vosk-venv/bin/pip install vosk

# Download Vosk Russian small model (~50MB)
RUN mkdir -p /app/vosk-model && \
    curl -L -o /tmp/vosk.zip \
        https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip && \
    unzip /tmp/vosk.zip -d /tmp && \
    cp -r /tmp/vosk-model-small-ru-0.22/* /app/vosk-model/ && \
    rm -rf /tmp/vosk.zip /tmp/vosk-model-small-ru-0.22

# Copy transcription script
COPY vosk_transcribe.py /app/vosk_transcribe.py
RUN chmod +x /app/vosk_transcribe.py

RUN addgroup --system taskbot && adduser --system taskbot --ingroup taskbot

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

RUN chown -R taskbot:taskbot /app

USER taskbot

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:ZCollectionInterval=30", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]