FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the fat JAR created by `sbt assembly`
COPY target/scala-*/chats-api-assembly-*.jar chats-api.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "chats-api.jar"]