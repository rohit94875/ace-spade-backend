# Use the Gradle image so the build does not download the wrapper distro (slow on homelab networks).
FROM gradle:7.6.4-jdk8 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
