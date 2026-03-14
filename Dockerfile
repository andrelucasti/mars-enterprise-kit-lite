# Stage 1: Build — Maven + Java 25 (Alpine for smaller image)
FROM maven:3.9-eclipse-temurin-25-alpine AS builder
WORKDIR /build

# Copy POM first (layer caching — dependencies re-downloaded only when pom.xml changes)
COPY pom.xml .

# Download dependencies (cached layer if pom.xml unchanged)
RUN mvn dependency:go-offline -q

# Copy source
COPY src/ src/

# Build fat JAR, skip tests (tests run in CI 'test' job separately)
RUN mvn clean package -DskipTests -q

# Stage 2: Run — minimal JRE image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from builder
COPY --from=builder /build/target/*.jar app.jar

# App runs on port 8082
EXPOSE 8082

# Health check (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
