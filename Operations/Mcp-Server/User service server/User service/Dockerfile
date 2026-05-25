FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY user-service-mcp.jar app.jar
LABEL org.opencontainers.image.title="Ecoskiller User MCP Server"
LABEL org.opencontainers.image.version="1.0.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
