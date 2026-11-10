# ==========================================
# BUILD STAGE
# ==========================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./

COPY shared/helphub-observability/pom.xml \
     shared/helphub-observability/pom.xml

COPY gateway/api-gateway/pom.xml \
     gateway/api-gateway/pom.xml

COPY services/identity-service/pom.xml \
     services/identity-service/pom.xml

COPY services/notification-service/pom.xml \
     services/notification-service/pom.xml

COPY shared/helphub-observability/src/ \
     shared/helphub-observability/src/

COPY services/notification-service/src/ \
     services/notification-service/src/

RUN mvn -B -ntp \
    -pl services/notification-service \
    -am package -DskipTests

# ==========================================
# RUNTIME STAGE
# ==========================================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build \
    /app/services/notification-service/target/notification-service-0.0.1-SNAPSHOT.jar \
    app.jar

EXPOSE 8083

USER 10001

ENTRYPOINT ["java", "-jar", "app.jar"]
