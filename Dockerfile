# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre
RUN useradd -m -u 1001 appuser
WORKDIR /app
COPY --from=build --chown=appuser /app/target/SBMP-0.0.1-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "app.jar"]
