### Build stage — compiles the app with Maven + JDK 17 ###
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies separately from source so a source-only change doesn't
# re-download the whole repository on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

### Runtime stage — slim JRE, no build tools ###
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S spring && adduser -S spring -G spring

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring:spring

EXPOSE 5000

# JVM sizes its heap as a percentage of the container's memory limit instead
# of a fixed -Xmx, so the same image behaves sanely whether it's capped at
# 512m (a small Oracle Cloud free-tier shape) or given more headroom later.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:5000/api/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
