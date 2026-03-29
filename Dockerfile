# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS frontend-build
WORKDIR /workspace/taro-frontend

COPY taro-frontend/package.json taro-frontend/package-lock.json ./
RUN npm ci

COPY taro-frontend ./
COPY src /workspace/src
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS app-build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/src/main/resources/static ./src/main/resources/static
RUN mvn -q -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

COPY --from=app-build /workspace/target/TARO-TIME_AWARE_ROUTING_Orchestrator-1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 10000

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
