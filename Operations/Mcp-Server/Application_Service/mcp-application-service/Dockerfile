FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY mcp-application-service.jar app.jar
LABEL org.opencontainers.image.title="Ecoskiller Application MCP Server"
LABEL org.opencontainers.image.version="1.0.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
