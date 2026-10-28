# Celá aplikace v jednom image: React build se zabalí do Spring Boot JAR (static/).
# Testy tu neběží (Testcontainers potřebují Docker) — spouštěj je před buildem: ./mvnw verify, npm test.

# ---- 1) Frontend --------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2) Backend + frontend do JAR ---------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
# pom.xml bere build frontendu z ../frontend/dist
COPY --from=frontend /app/frontend/dist /app/frontend/dist
RUN ./mvnw -B -q package -DskipTests && cp target/zazitky-*.jar /app/app.jar

# ---- 3) Runtime ---------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=backend /app/app.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
