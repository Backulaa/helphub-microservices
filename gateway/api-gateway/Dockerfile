# ==========================================
# STAGE 1: BUILD APPLICATION
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source code
COPY src ./src

# Build Spring Boot application
RUN mvn -B clean package -DskipTests


# ==========================================
# STAGE 2: RUN APPLICATION
# ==========================================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy executable JAR
COPY --from=build \
    /app/target/api-gateway-0.0.1-SNAPSHOT.jar \
    app.jar

# Run as non-root user
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
