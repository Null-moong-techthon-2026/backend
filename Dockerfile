FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
COPY --from=build --chown=app:app /workspace/build/libs/app.jar ./app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=deploy
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

