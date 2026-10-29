# ==========================================
# Stage 1: Build application
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven configuration first for layer caching
COPY pom.xml .

# Download dependencies
RUN mvn -B -ntp dependency:go-offline

# Copy application source
COPY src ./src

# Build executable Spring Boot JAR
RUN mvn -B -ntp clean package -DskipTests


# ==========================================
# Stage 2: Runtime
# ==========================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy executable JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Run application as non-root user
USER 10001

# Notification Service HTTP port
EXPOSE 8083

# Start Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]
