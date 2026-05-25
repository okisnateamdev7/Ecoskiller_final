package io.ecoskiller.prosody.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ecoskiller.prosody.agents.*;
import io.ecoskiller.prosody.security.RateLimiter;
import io.ecoskiller.prosody.security.RequestValidator;
import io.ecoskiller.prosody.security.AuditLogger;
import io.ecoskiller.prosody.config.ServerConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;

/**
 * Ecoskiller Prosody MCP Server (CAT-PROSODY)
 *
 * Secure Java MCP Server for XMPP Signaling & Presence Management.
 * Implements 14 agents covering all Prosody operations:
 *   - Room lifecycle (create, close, query)
 *   - Participant management (join, leave, roster, presence)
 *   - Authentication (JWT validation, SASL)
 *   - Signaling relay (SDP/ICE exchange with Jitsi)
 *   - Connection management (health, metrics, rate limiting)
 *   - Cluster operations (S2S federation, pod status)
 *   - Event emission (Kafka room/participant events)
 *   - Security audit (auth failures, access logs)
 *
 * Transport: stdio (stdin/stdout)
 * Protocol:  JSON-RPC 2.0 / MCP 2024-11-05
 * Security:  JWT auth, rate limiting, input validation, audit logging
 */
public class ProsodyMcpServer {

    private static final Logger LOGGER = Logger.getLogger(ProsodyMcpServer.class.getName());
    private static final String MCP_VERSION = "2024-11-05";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String SERVER_NAME = "mcp-prosody-ecoskiller";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AgentHandler> agents = new LinkedHashMap<>();
    private final RateLimiter rateLimiter;
    private final RequestValidator validator;
    private final AuditLogger auditLogger;
    private final ServerConfig config;

    // ── Agent registry ─────────────────────────────────────────────────────────

    public ProsodyMcpServer() {
        this.config = ServerConfig.load();
        this.rateLimiter = new RateLimiter(config);
        this.validator = new RequestValidator();
        this.auditLogger = new AuditLogger(config);

        // Register all 14 agents
        registerAgents();
        LOGGER.info("ProsodyMcpServer initialised — " + agents.size() + " agents loaded.");
    }

    private void registerAgents() {
        agents.putAll(AgentFactory.createAllAgents(config, auditLogger, rateLimiter));
    }

    // ── Main loop ───────────────────────────────────────────────────────────────

    public void run() throws IOException {
        LOGGER.info("ProsodyMcpServer listening on stdin/stdout (JSON-RPC 2.0)");

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String response = handleRawMessage(line);
            if (response != null) {
                writer.println(response);
            }
        }
    }

    // ── JSON-RPC dispatcher ─────────────────────────────────────────────────────

    public String handleRawMessage(String rawJson) {
        JsonNode request;
        try {
            request = mapper.readTree(rawJson);
        } catch (Exception e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }

        JsonNode idNode = request.get("id");
        Object id = idNode == null ? null : (idNode.isNumber() ? idNode.asLong() : idNode.asText());

        String method = request.has("method") ? request.get("method").asText() : "";
        JsonNode params = request.has("params") ? request.get("params") : mapper.createObjectNode();

        // Security: rate limit all requests (keyed by session/ip from params or global)
        String clientKey = extractClientKey(params);
        if (!rateLimiter.allowRequest(clientKey)) {
            auditLogger.warn("RATE_LIMIT_HIT", clientKey, method);
            return errorResponse(id, -32000, "Rate limit exceeded. Slow down.");
        }

        try {
            return switch (method) {
                case "initialize"    -> handleInitialize(id, params);
                case "ping"          -> handlePing(id);
                case "tools/list"    -> handleToolsList(id);
                case "tools/call"    -> handleToolsCall(id, params);
                default              -> errorResponse(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled error processing method: " + method, e);
            return errorResponse(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ── MCP method handlers ─────────────────────────────────────────────────────

    private String handleInitialize(Object id, JsonNode params) throws Exception {
        auditLogger.info("INITIALIZE", "server", "MCP handshake");
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", MCP_VERSION);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        serverInfo.put("description",
            "Ecoskiller Prosody MCP — XMPP Signaling & Presence Management Server");

        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        return successResponse(id, result);
    }

    private String handlePing(Object id) throws Exception {
        return successResponse(id, mapper.createObjectNode().put("status", "pong"));
    }

    private String handleToolsList(Object id) throws Exception {
        ArrayNode tools = mapper.createArrayNode();
        for (Map.Entry<String, AgentHandler> entry : agents.entrySet()) {
            tools.add(entry.getValue().getToolDefinition());
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", tools);
        return successResponse(id, result);
    }

    private String handleToolsCall(Object id, JsonNode params) throws Exception {
        // Input validation
        if (!params.has("name")) {
            return errorResponse(id, -32602, "Missing tool name");
        }
        String toolName = params.get("name").asText();
        JsonNode args = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        // Validate tool name (prevent injection)
        if (!validator.isValidToolName(toolName)) {
            auditLogger.warn("INVALID_TOOL_NAME", "unknown", toolName);
            return errorResponse(id, -32602, "Invalid tool name: " + toolName);
        }

        AgentHandler agent = agents.get(toolName);
        if (agent == null) {
            return errorResponse(id, -32601, "Unknown tool: " + toolName);
        }

        // Validate arguments schema
        List<String> validationErrors = validator.validate(toolName, args);
        if (!validationErrors.isEmpty()) {
            auditLogger.warn("VALIDATION_FAIL", "unknown", toolName + ": " + validationErrors);
            return errorResponse(id, -32602, "Validation errors: " + validationErrors);
        }

        auditLogger.info("TOOL_CALL", extractClientKey(args), toolName);

        try {
            JsonNode agentResult = agent.execute(args);
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = result.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", mapper.writeValueAsString(agentResult));
            result.put("isError", false);
            return successResponse(id, result);
        } catch (SecurityException se) {
            auditLogger.error("SECURITY_VIOLATION", extractClientKey(args), toolName + ": " + se.getMessage());
            return errorResponse(id, -32000, "Security violation: " + se.getMessage());
        } catch (Exception e) {
            auditLogger.error("AGENT_ERROR", extractClientKey(args), toolName + ": " + e.getMessage());
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = result.putArray("content");
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            result.put("isError", true);
            return successResponse(id, result);
        }
    }

    // ── JSON-RPC helpers ────────────────────────────────────────────────────────

    private String successResponse(Object id, JsonNode result) throws Exception {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id == null ? mapper.nullNode() :
            (id instanceof Long ? mapper.getNodeFactory().numberNode((Long) id)
                                : mapper.getNodeFactory().textNode(id.toString())));
        resp.set("result", result);
        return mapper.writeValueAsString(resp);
    }

    private String errorResponse(Object id, int code, String message) {
        try {
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            resp.set("id", id == null ? mapper.nullNode() :
                (id instanceof Long ? mapper.getNodeFactory().numberNode((Long) id)
                                    : mapper.getNodeFactory().textNode(id.toString())));
            ObjectNode error = resp.putObject("error");
            error.put("code", code);
            error.put("message", message);
            return mapper.writeValueAsString(resp);
        } catch (Exception ex) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal serialisation error\"}}";
        }
    }

    private String extractClientKey(JsonNode params) {
        if (params != null && params.has("jwt_token")) {
            // Use first 16 chars of JWT as key (don't log full token)
            String token = params.get("jwt_token").asText();
            return token.length() > 16 ? token.substring(0, 16) + "..." : token;
        }
        return "anonymous";
    }

    private String escapeJson(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ── Entry point ─────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        configureLogging();
        LOGGER.info("Starting Ecoskiller Prosody MCP Server v" + SERVER_VERSION);
        new ProsodyMcpServer().run();
    }

    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        // Log to stderr (stdout is reserved for JSON-RPC)
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new SimpleFormatter());
        root.addHandler(handler);
        // Remove default handler to avoid duplicate stderr output
        for (Handler h : root.getHandlers()) {
            if (h != handler) root.removeHandler(h);
        }
        root.addHandler(handler);
    }
}
