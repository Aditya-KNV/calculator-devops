# syntax=docker/dockerfile:1

# ---- Stage 1: build the executable JAR with Maven + JDK ----
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only the POM first so the dependency layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Stage 2: slim JRE runtime containing only the JAR ----
FROM eclipse-temurin:17-jre-alpine

LABEL org.opencontainers.image.title="calculator-api" \
      org.opencontainers.image.description="Calculator REST API - CSE 816 SPE Mini-Project"

WORKDIR /app

# Run as an unprivileged user
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build --chown=spring:spring /app/target/calculator-api.jar app.jar
USER spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# exec so java is PID 1 and receives SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
