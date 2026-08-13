FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
FROM maven:3.9.4-eclipse-temurin-25 as build
WORKDIR /workspace
COPY pom.xml mvnw* ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/digital-asset-custody-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
