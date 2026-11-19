FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre AS runtime-base
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app app \
    && mkdir -p /var/lib/boothrock/media \
    && chown app:app /var/lib/boothrock/media
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=deploy
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS prebuilt
COPY --chown=app:app app.jar /app/app.jar

FROM runtime-base AS final
COPY --from=build --chown=app:app /workspace/build/libs/app.jar /app/app.jar
