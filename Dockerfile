# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Download dependencies first (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build (tests already ran in the Jenkins "Build & Test" stage)
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user
RUN useradd --system --uid 1001 appuser
COPY --from=build /src/target/app.jar app.jar
USER 1001

EXPOSE 8080
# Respect the container memory limit set in Kubernetes
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
