FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY job-service-mcp-1.0.0.jar app.jar
LABEL org.opencontainers.image.title="Ecoskiller Job MCP Server"
LABEL org.opencontainers.image.version="1.0.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
