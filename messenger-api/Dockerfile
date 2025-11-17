FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the fat JAR created by `sbt assembly`
COPY target/scala-*/messenger-api-assembly-*.jar messenger-api.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "messenger-api.jar"]