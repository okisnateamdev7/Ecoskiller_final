package com.ecoskiller.mcp.redis;

import com.ecoskiller.mcp.redis.config.ServerConfig;
import com.ecoskiller.mcp.redis.security.AuditLogger;
import com.ecoskiller.mcp.redis.security.InputValidator;
import com.ecoskiller.mcp.redis.tools.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ecoskiller Redis State Store MCP Server
 *
 * Implements Model Context Protocol (JSON-RPC 2.0) over stdio.
 * Provides 18 tools for Redis state management, GD orchestration,
 * leaderboards, OTP, distributed locking, Pub/Sub, and more.
 *
 * Security: all inputs are validated, tenant-scoped, and audit-logged.
 * Protocol: MCP 2024-11-05 | Transport: stdio | Format: JSON-RPC 2.0
 */
public class RedisStateMcpServer {

    private static final Logger LOGGER = Logger.getLogger(RedisStateMcpServer.class.getName());
    private static final String MCP_VERSION  = "2024-11-05";
    private static final String SERVER_NAME  = "redis-state-store";
    private static final String SERVER_VER   = "1.0.0";

    private final Map<String, McpTool> tools = new HashMap<>();
    private final AuditLogger          auditLogger;
    private final InputValidator       validator;

    public RedisStateMcpServer() {
        this.auditLogger = new AuditLogger();
        this.validator   = new InputValidator();
        registerTools();
    }

    // ─── Tool Registry ──────────────────────────────────────────────────────────

    private void registerTools() {
        register(new GdSessionStateTool());
        register(new GdTimerTool());
        register(new GdQueueTool());
        register(new OtpStoreTool());
        register(new DistributedLockTool());
        register(new RateLimitTool());
        register(new LeaderboardTool());
        register(new InterviewTimerTool());
        register(new SessionAffinityTool());
        register(new DojoMatchTool());
        register(new TenantConfigCacheTool());
        register(new PubSubBroadcastTool());
        register(new KeyspaceNotificationTool());
        register(new HealthCheckTool());
        register(new KeyInspectTool());
        register(new BillingMeterTool());
        register(new BackupStatusTool());
        register(new TenantKeyFlushTool());
    }

    private void register(McpTool tool) {
        tools.put(tool.getName(), tool);
    }

    // ─── Main Loop ───────────────────────────────────────────────────────────────

    public void run() throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  StandardCharsets.UTF_8));
        PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);

        LOGGER.info("[MCP] Redis State Store Server starting — MCP " + MCP_VERSION);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                JSONObject request  = new JSONObject(line);
                JSONObject response = handleRequest(request);
                if (response != null) {
                    out.println(response.toString());
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[MCP] Fatal parse error", e);
                out.println(errorResponse(null, -32700, "Parse error: " + e.getMessage()).toString());
            }
        }
    }

    // ─── Request Dispatch ────────────────────────────────────────────────────────

    private JSONObject handleRequest(JSONObject req) {
        Object id     = req.opt("id");
        String method = req.optString("method", "");

        auditLogger.logRequest(method, req);

        return switch (method) {
            case "initialize"  -> handleInitialize(id, req);
            case "ping"        -> handlePing(id);
            case "tools/list"  -> handleToolsList(id);
            case "tools/call"  -> handleToolsCall(id, req);
            default            -> {
                if (method.startsWith("notifications/")) yield null; // fire-and-forget
                yield errorResponse(id, -32601, "Method not found: " + method);
            }
        };
    }

    // ─── MCP Methods ─────────────────────────────────────────────────────────────

    private JSONObject handleInitialize(Object id, JSONObject req) {
        JSONObject serverInfo = new JSONObject()
                .put("name", SERVER_NAME)
                .put("version", SERVER_VER);

        JSONObject capabilities = new JSONObject()
                .put("tools", new JSONObject().put("listChanged", false));

        JSONObject result = new JSONObject()
                .put("protocolVersion", MCP_VERSION)
                .put("serverInfo", serverInfo)
                .put("capabilities", capabilities);

        LOGGER.info("[MCP] Client initialized — " + req.optJSONObject("clientInfo"));
        return successResponse(id, result);
    }

    private JSONObject handlePing(Object id) {
        return successResponse(id, new JSONObject());
    }

    private JSONObject handleToolsList(Object id) {
        JSONArray toolArray = new JSONArray();
        for (McpTool tool : tools.values()) {
            toolArray.put(tool.getSchema());
        }
        return successResponse(id, new JSONObject().put("tools", toolArray));
    }

    private JSONObject handleToolsCall(Object id, JSONObject req) {
        JSONObject params   = req.optJSONObject("params");
        if (params == null) return errorResponse(id, -32602, "Missing params");

        String     toolName = params.optString("name");
        JSONObject args     = params.optJSONObject("arguments");
        if (args == null) args = new JSONObject();

        McpTool tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "Unknown tool: " + toolName);
        }

        // Security: validate all inputs before execution
        try {
            validator.validate(toolName, args);
        } catch (SecurityException se) {
            auditLogger.logSecurityViolation(toolName, args, se.getMessage());
            return errorResponse(id, -32602, "Security validation failed: " + se.getMessage());
        }

        try {
            JSONObject callResult = tool.execute(args);
            auditLogger.logSuccess(toolName, args);
            return successResponse(id, callResult);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[MCP] Tool execution error: " + toolName, e);
            auditLogger.logError(toolName, args, e.getMessage());
            JSONArray content = new JSONArray().put(
                    new JSONObject().put("type", "text").put("text", "Error: " + e.getMessage())
            );
            return successResponse(id, new JSONObject().put("content", content).put("isError", true));
        }
    }

    // ─── Response Builders ───────────────────────────────────────────────────────

    static JSONObject successResponse(Object id, JSONObject result) {
        JSONObject r = new JSONObject().put("jsonrpc", "2.0").put("result", result);
        if (id != null) r.put("id", id);
        return r;
    }

    static JSONObject errorResponse(Object id, int code, String message) {
        JSONObject err = new JSONObject().put("code", code).put("message", message);
        JSONObject r   = new JSONObject().put("jsonrpc", "2.0").put("error", err);
        if (id != null) r.put("id", id);
        return r;
    }

    // ─── Entry Point ─────────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        ServerConfig.loadFromEnv();
        new RedisStateMcpServer().run();
    }
}
