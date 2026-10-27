# Builds and runs the Spring Boot API. Used by Render (see render.yaml); also works with any Docker host.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 jobtrack
COPY --from=build /app/app.jar app.jar
USER jobtrack
# Free hosting plans have little memory, so let the JVM size its heap from the container limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
# Render passes the port to listen on in $PORT; fall back to 8080 elsewhere.
CMD ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-8080}"]
