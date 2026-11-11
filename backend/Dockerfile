# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

# ---- run ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/backend-*.jar app.jar
# Render free tier has 512 MB RAM - keep the heap inside it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
