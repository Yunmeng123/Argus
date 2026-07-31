FROM node:22-alpine AS web-build
WORKDIR /build/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS app-build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/ src/
COPY samples/ samples/
COPY --from=web-build /build/src/main/resources/static/ src/main/resources/static/
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S argus && adduser -S -G argus argus
WORKDIR /app
COPY --from=app-build /build/target/argus-*.jar app.jar
COPY --from=app-build /build/samples/ samples/
RUN mkdir -p data reports && chown -R argus:argus /app
USER argus
EXPOSE 18080
VOLUME ["/app/data", "/app/reports"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
