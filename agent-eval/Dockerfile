FROM eclipse-temurin:21-jre-alpine
WORKDIR /eval
COPY target/agent-eval-*.jar agent-eval.jar
ENTRYPOINT ["java", "-jar", "agent-eval.jar"]
