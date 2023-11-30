FROM gradle:8.4.0-jdk17 AS build
COPY --chown=gradle:gradle build.gradle.kts gradle.properties settings.gradle.kts /home/gradle/
RUN gradle clean build --no-daemon --info
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle buildFatJar --no-daemon --info

FROM eclipse-temurin:17
EXPOSE 8080:8080
RUN mkdir /appbus
COPY --from=build /home/gradle/src/build/libs/*.jar /app/en.jar
COPY --from=build /home/gradle/src/*.yaml /app/
WORKDIR /app
ENTRYPOINT ["java","-jar","/app/en.jar"]

