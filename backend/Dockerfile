# syntax=docker/dockerfile:1.7
# ─── build stage ───
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ─── runtime stage ───
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r magent && useradd -r -g magent magent
COPY --from=build /app/target/*.jar app.jar
USER magent
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]