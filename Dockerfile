FROM gradle:8.1.1-jdk17 AS TEMP_BUILD_IMAGE
ENV APP_HOME=/app/
WORKDIR $APP_HOME
COPY build.gradle settings.gradle $APP_HOME

COPY gradle $APP_HOME/gradle
COPY --chown=gradle:gradle . /home/gradle/src
USER root
RUN chown -R gradle /home/gradle/src

COPY . .
RUN gradle shadowJar

FROM eclipse-temurin:20
ENV ARTIFACT_NAME='Reddit Lucky Number Bot-1.0-all.jar'
ENV APP_HOME=/app/

WORKDIR $APP_HOME
COPY --from=TEMP_BUILD_IMAGE $APP_HOME/build/libs/$ARTIFACT_NAME .
COPY --from=TEMP_BUILD_IMAGE $APP_HOME/koyeb.sh .
RUN chmod +x ./koyeb.sh
WORKDIR $APP_HOME

ENTRYPOINT ["java", "-jar", "Reddit Lucky Number Bot-1.0-all.jar"]
#ENTRYPOINT ["./koyeb.sh"]