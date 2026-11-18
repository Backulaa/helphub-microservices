# ==========================================
# BUILD STAGE
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Root Maven aggregator
COPY pom.xml ./

# Copy all module POMs for Maven reactor discovery
COPY shared/helphub-observability/pom.xml \
     shared/helphub-observability/pom.xml

COPY gateway/api-gateway/pom.xml \
     gateway/api-gateway/pom.xml

COPY services/identity-service/pom.xml \
     services/identity-service/pom.xml

COPY services/media-service/pom.xml \
     services/media-service/pom.xml

COPY services/notification-service/pom.xml \
     services/notification-service/pom.xml

# Copy source of required modules
COPY shared/helphub-observability/src/ \
     shared/helphub-observability/src/

COPY services/identity-service/src/ \
     services/identity-service/src/

# Build identity and its shared dependencies
RUN mvn -B -ntp \
    -pl services/identity-service \
    -am package -DskipTests

# ==========================================
# RUNTIME STAGE
# ==========================================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build \
    /app/services/identity-service/target/identity-service-0.0.1-SNAPSHOT.jar \
    app.jar

EXPOSE 8081

USER 10001

ENTRYPOINT ["java", "-jar", "app.jar"]
