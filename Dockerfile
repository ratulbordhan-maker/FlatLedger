# Builds and runs the whole app in a container. Used by Render (Option B).
# You don't need Docker on your own computer: Render runs this file for you.
#
# Three stages, each starting from a ready-made image:
#   1. node  : build the React website
#   2. maven : put the website inside the backend and build the jar
#   3. java  : a small image that only runs the jar

# ---- 1. website
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# ---- 2. backend jar (tests run on your computer with "mvn test", so they're skipped here)
FROM maven:3.9-eclipse-temurin-21 AS jar
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# ---- 3. run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=jar /app/target/flat-meal-manager.jar app.jar
USER app
ENV TZ=Asia/Dhaka \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
