# ---------------------------------------------------------------------------
# Stage 1: Build
# Uses the official Maven image so no wrapper or local Maven install is needed.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Cache dependency layer — only re-runs if pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build (tests are skipped here; CI runs them separately before image push)
COPY src ./src
RUN mvn package -DskipTests -q

# ---------------------------------------------------------------------------
# Stage 2: Runtime
# Minimal JRE image — no JDK, no Maven, significantly smaller attack surface.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# JVM flags explained:
#   -Xms256m            : initial heap (fast startup, avoids GC thrash on boot)
#   -Xmx1g              : max heap (comfortable on DO basic-s 2 GB instance)
#   -XX:+UseG1GC        : G1 GC — lower pause times for HTTP workloads
#   -XX:MaxGCPauseMillis: target 200ms GC pauses
#   -Djava.security.egd : faster SecureRandom (important for UUID generation)
ENTRYPOINT ["java", \
  "-Xms256m", \
  "-Xmx1g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
