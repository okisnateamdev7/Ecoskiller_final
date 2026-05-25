FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY mcp-auth-service.jar app.jar
LABEL org.opencontainers.image.title="Ecoskiller Auth MCP Server"
LABEL org.opencontainers.image.version="1.0.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
