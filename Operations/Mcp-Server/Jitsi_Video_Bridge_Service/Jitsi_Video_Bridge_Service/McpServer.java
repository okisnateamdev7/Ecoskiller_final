package io.ecoskiller.mcp.jitsi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;

/**
 * Ecoskiller | Jitsi Video Bridge MCP Server
 * CAT-JVB — Real-Time Video Communication & Conference Management
 *
 * MCP Server in Java | 14 Agents | Priority: PRODUCTION-CRITICAL
 *
 * Transport: stdio (stdin/stdout)
 * Format:    JSON-RPC 2.0
 * MCP Version: 2024-11-05
 */
public class McpServer {

    private static final Logger LOGGER = Logger.getLogger(McpServer.class.getName());
    private static final String MCP_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "mcp-jitsi-videobridge";
    private static final String SERVER_VERSION = "2.1.0";

    private final Map<String, ToolHandler> tools = new LinkedHashMap<>();
    private final JwtValidator jwtValidator;
    private final RateLimiter rateLimiter;

    public McpServer() {
        this.jwtValidator  = new JwtValidator();
        this.rateLimiter   = new RateLimiter(60, 60_000); // 60 req/min per tool

        registerTools();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tool Registration
    // ─────────────────────────────────────────────────────────────────────────

    private void registerTools() {
        register(new ConferenceCreateTool());
        register(new ConferenceTerminateTool());
        register(new ConferenceStatusTool());
        register(new ParticipantJoinTool());
        register(new ParticipantRemoveTool());
        register(new ActiveSpeakerDetectionTool());
        register(new BandwidthManagementTool());
        register(new RecordingControlTool());
        register(new RecordingStatusTool());
        register(new BridgeHealthTool());
        register(new BridgeScalingTool());
        register(new KafkaEventEmitTool());
        register(new JwtGenerateTool());
        register(new NetworkDiagnosticsTool());
    }

    private void register(ToolHandler handler) {
        tools.put(handler.getName(), handler);
        LOGGER.info("Registered tool: " + handler.getName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main I/O Loop
    // ─────────────────────────────────────────────────────────────────────────

    public void run() throws IOException {
        LOGGER.info("Jitsi Video Bridge MCP Server starting (MCP " + MCP_VERSION + ")");

        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  StandardCharsets.UTF_8));
        PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            String response = handleRequest(line);
            if (response != null) {
                out.println(response);
                out.flush();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Request Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    String handleRequest(String rawJson) {
        String id     = null;
        String method = null;

        try {
            Map<String, Object> req = JsonParser.parseObject(rawJson);
            id     = getString(req, "id");
            method = getString(req, "method");

            if (method == null) {
                return errorResponse(id, -32600, "Invalid Request: missing method");
            }

            return switch (method) {
                case "initialize"   -> handleInitialize(id, req);
                case "ping"         -> handlePing(id);
                case "tools/list"   -> handleToolsList(id);
                case "tools/call"   -> handleToolsCall(id, req);
                default             -> errorResponse(id, -32601, "Method not found: " + method);
            };

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Request processing error", e);
            return errorResponse(id, -32603, "Internal error: " + sanitizeError(e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MCP Method Handlers
    // ─────────────────────────────────────────────────────────────────────────

    private String handleInitialize(String id, Map<String, Object> req) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", MCP_VERSION);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name",    SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Collections.singletonMap("listChanged", false));
        result.put("capabilities", capabilities);

        return successResponse(id, result);
    }

    private String handlePing(String id) {
        return successResponse(id, Collections.emptyMap());
    }

    private String handleToolsList(String id) {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (ToolHandler handler : tools.values()) {
            toolList.add(handler.getSchema());
        }
        return successResponse(id, Collections.singletonMap("tools", toolList));
    }

    @SuppressWarnings("unchecked")
    private String handleToolsCall(String id, Map<String, Object> req) {
        Map<String, Object> params = (Map<String, Object>) req.get("params");
        if (params == null) return errorResponse(id, -32602, "Missing params");

        String toolName = getString(params, "name");
        if (toolName == null) return errorResponse(id, -32602, "Missing tool name");

        // ── Rate limiting ──────────────────────────────────────────────────
        if (!rateLimiter.allow(toolName)) {
            return errorResponse(id, -32029, "Rate limit exceeded for tool: " + toolName);
        }

        // ── JWT validation (for sensitive tools) ──────────────────────────
        Map<String, Object> toolArgs = (Map<String, Object>) params.getOrDefault("arguments", new HashMap<>());
        String jwtToken = getString(toolArgs, "_auth_token");
        if (requiresAuth(toolName) && !jwtValidator.validate(jwtToken)) {
            return errorResponse(id, -32401, "Unauthorized: valid JWT required for " + toolName);
        }

        // ── Dispatch to tool ───────────────────────────────────────────────
        ToolHandler handler = tools.get(toolName);
        if (handler == null) {
            return errorResponse(id, -32601, "Unknown tool: " + toolName);
        }

        // Strip internal auth field before handing to tool
        Map<String, Object> safeArgs = new HashMap<>(toolArgs);
        safeArgs.remove("_auth_token");

        try {
            Object toolResult = handler.execute(safeArgs);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text", "text", JsonSerializer.toJson(toolResult))));
            result.put("isError", false);
            return successResponse(id, result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of("type", "text", "text",
                    "{\"error\":\"" + sanitizeError(e.getMessage()) + "\"}")));
            result.put("isError", true);
            return successResponse(id, result);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static final Set<String> AUTH_REQUIRED_TOOLS = Set.of(
        "conference_create",
        "conference_terminate",
        "participant_remove",
        "recording_control",
        "bridge_scaling",
        "jwt_generate"
    );

    private boolean requiresAuth(String toolName) {
        return AUTH_REQUIRED_TOOLS.contains(toolName);
    }

    /** Prevent leaking stack traces or internal paths in error messages. */
    private String sanitizeError(String msg) {
        if (msg == null) return "Unknown error";
        // Strip any path-like segments or class names
        return msg.replaceAll("at [\\w\\.]+\\(.*?\\)", "")
                  .replaceAll("/[\\w/]+\\.java", "")
                  .trim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON-RPC helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String successResponse(String id, Object result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return JsonSerializer.toJson(resp);
    }

    private String errorResponse(String id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", error);
        return JsonSerializer.toJson(resp);
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return (val instanceof String) ? (String) val : (val != null ? val.toString() : null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entry Point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        // Redirect JUL logging to stderr so it never pollutes the MCP stdio channel
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler stderr = new ConsoleHandler();
        // stderr.setOutputStream(System.err); // setOutputStream is protected; ConsoleHandler defaults to System.err
        stderr.setLevel(Level.ALL);
        root.addHandler(stderr);
        root.setLevel(Level.INFO);

        new McpServer().run();
    }
}
