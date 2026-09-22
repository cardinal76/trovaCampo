# --- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Il pom da solo prima del sorgente: finche' le dipendenze non cambiano,
# Docker riusa il layer con il repository Maven gia' popolato.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Utente non privilegiato: chi esce dall'applicazione non si ritrova root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

EXPOSE 3000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
