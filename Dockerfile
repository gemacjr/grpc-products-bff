# Build on glibc (Debian) so protoc-gen-grpc-java native binaries work.
# Alpine/musl cannot execute the downloaded protoc plugins.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
USER app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
