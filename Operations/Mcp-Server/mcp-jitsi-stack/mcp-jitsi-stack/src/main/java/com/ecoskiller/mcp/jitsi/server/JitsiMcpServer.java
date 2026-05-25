package com.ecoskiller.mcp.jitsi.server;

import com.ecoskiller.mcp.jitsi.agents.*;
import com.ecoskiller.mcp.jitsi.security.McpSecurityManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.util.*;
import java.util.logging.*;

/**
 * Ecoskiller | CAT-Jitsi — Jitsi Media Stack MCP Server
 * Secure Java MCP Server — 12 Agents
 * Protocol: JSON-RPC 2.0 over stdio
 * MCP Version: 2024-11-05
 *
 * Components: JVB · Jicofo · Prosody · Jitsi Web
 * Security: JWT Auth · SRTP · DTLS · Input Validation
 */
public class JitsiMcpServer {

    private static final Logger LOGGER = Logger.getLogger(JitsiMcpServer.class.getName());
    private static final String MCP_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "mcp-jitsi-stack";
    private static final String SERVER_VERSION = "1.0.0";

    private final ObjectMapper mapper = new ObjectMapper();
    private final McpSecurityManager securityManager = new McpSecurityManager();
    private final Map<String, AgentHandler> agentRegistry = new LinkedHashMap<>();

    public JitsiMcpServer() {
        registerAgents();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Agent Registration
    // ─────────────────────────────────────────────────────────────────────────

    private void registerAgents() {
        agentRegistry.put("jvb_stream_routing",         new JvbStreamRoutingAgent());
        agentRegistry.put("jvb_quality_metrics",        new JvbQualityMetricsAgent());
        agentRegistry.put("jicofo_room_lifecycle",      new JicofoRoomLifecycleAgent());
        agentRegistry.put("jicofo_participant_routing", new JicofoParticipantRoutingAgent());
        agentRegistry.put("prosody_jwt_validation",     new ProsodyJwtValidationAgent());
        agentRegistry.put("prosody_xmpp_signalling",    new ProsodyXmppSignallingAgent());
        agentRegistry.put("jitsi_web_session",          new JitsiWebSessionAgent());
        agentRegistry.put("coturn_nat_traversal",       new CoturnNatTraversalAgent());
        agentRegistry.put("gd_session_orchestrator",    new GdSessionOrchestratorAgent());
        agentRegistry.put("media_security_audit",       new MediaSecurityAuditAgent());
        agentRegistry.put("freeswitch_pstn_bridge",     new FreeswitchPstnBridgeAgent());
        agentRegistry.put("jitsi_health_monitor",       new JitsiHealthMonitorAgent());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main I/O Loop
    // ─────────────────────────────────────────────────────────────────────────

    public void run() {
        LOGGER.info("Jitsi MCP Server starting — " + SERVER_VERSION);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String response = handleRequest(line);
                if (response != null) {
                    writer.println(response);
                    writer.flush();
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Fatal I/O error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    private String handleRequest(String rawJson) {
        try {
            // Security: validate JSON before parsing
            securityManager.validateRawInput(rawJson);

            JsonNode req = mapper.readTree(rawJson);
            String method = req.path("method").asText();
            JsonNode id = req.get("id");
            JsonNode params = req.get("params");

            return switch (method) {
                case "initialize"   -> handleInitialize(id, params);
                case "tools/list"   -> handleToolsList(id);
                case "tools/call"   -> handleToolsCall(id, params);
                case "ping"         -> handlePing(id);
                default             -> errorResponse(id, -32601, "Method not found: " + method);
            };

        } catch (SecurityException e) {
            LOGGER.warning("Security violation: " + e.getMessage());
            return errorResponse(null, -32600, "Security violation: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.severe("Request handling error: " + e.getMessage());
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MCP Methods
    // ─────────────────────────────────────────────────────────────────────────

    private String handleInitialize(JsonNode id, JsonNode params) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", MCP_VERSION);

        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");

        return successResponse(id, result);
    }

    private String handleToolsList(JsonNode id) throws Exception {
        ArrayNode tools = mapper.createArrayNode();

        for (Map.Entry<String, AgentHandler> entry : agentRegistry.entrySet()) {
            ObjectNode tool = tools.addObject();
            tool.put("name", entry.getKey());
            tool.put("description", entry.getValue().getDescription());
            tool.set("inputSchema", entry.getValue().getInputSchema(mapper));
        }

        ObjectNode result = mapper.createObjectNode();
        result.set("tools", tools);
        return successResponse(id, result);
    }

    private String handleToolsCall(JsonNode id, JsonNode params) throws Exception {
        if (params == null) {
            return errorResponse(id, -32602, "Missing params");
        }

        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        // Security: validate tool name
        securityManager.validateToolName(toolName);

        AgentHandler handler = agentRegistry.get(toolName);
        if (handler == null) {
            return errorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        // Security: validate arguments
        securityManager.validateArguments(arguments);

        try {
            AgentResult agentResult = handler.execute(arguments);
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = result.putArray("content");

            ObjectNode textContent = content.addObject();
            textContent.put("type", "text");
            textContent.put("text", mapper.writeValueAsString(agentResult));

            return successResponse(id, result);

        } catch (Exception e) {
            LOGGER.warning("Agent execution error [" + toolName + "]: " + e.getMessage());
            return errorResponse(id, -32603, "Agent error: " + e.getMessage());
        }
    }

    private String handlePing(JsonNode id) throws Exception {
        return successResponse(id, mapper.createObjectNode());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON-RPC Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String successResponse(JsonNode id, JsonNode result) throws Exception {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.set("id", id);
        resp.set("result", result);
        return mapper.writeValueAsString(resp);
    }

    private String errorResponse(JsonNode id, int code, String message) {
        try {
            ObjectNode resp = mapper.createObjectNode();
            resp.put("jsonrpc", "2.0");
            if (id != null) resp.set("id", id);
            ObjectNode error = resp.putObject("error");
            error.put("code", code);
            // Security: sanitize error messages before returning
            error.put("message", securityManager.sanitizeErrorMessage(message));
            return mapper.writeValueAsString(resp);
        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Internal error\"}}";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry Point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        configureLogging();
        new JitsiMcpServer().run();
    }

    private static class StderrHandler extends ConsoleHandler {
        public StderrHandler() {
            super();
            setOutputStream(System.err);
        }
    }

    private static void configureLogging() {
        Logger rootLogger = Logger.getLogger("");
        // MCP uses stdio — log to stderr only
        for (Handler h : rootLogger.getHandlers()) rootLogger.removeHandler(h);
        StderrHandler stderrHandler = new StderrHandler();
        stderrHandler.setLevel(Level.INFO);
        rootLogger.addHandler(stderrHandler);
        rootLogger.setLevel(Level.INFO);
    }
}
