FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN useradd -ms /bin/bash appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar
COPY Dev /app/Dev

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
