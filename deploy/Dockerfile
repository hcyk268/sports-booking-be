FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --chown=10001:10001 app.jar app.jar
USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
