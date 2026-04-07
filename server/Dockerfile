FROM gradle:8.11.1-jdk21 AS build
WORKDIR /workspace
# Build only core+server modules to avoid requiring Android toolchain plugins.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN ./gradlew :server:shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /workspace/server/build/libs/*.jar /app/paisho-server.jar
EXPOSE 8080
ENV PAISHO_DB_PATH=/data/paisho.db
CMD ["java", "-jar", "/app/paisho-server.jar"]
