FROM gradle:8.11.1-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle :server:shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /workspace/server/build/libs/paisho-server-all.jar /app/paisho-server.jar
EXPOSE 8080
ENV PAISHO_DB_PATH=/data/paisho.db
CMD ["java", "-jar", "/app/paisho-server.jar"]
