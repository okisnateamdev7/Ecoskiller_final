package com.ecoskiller.mcp.webrtc.server;

import com.ecoskiller.mcp.webrtc.agents.*;
import com.ecoskiller.mcp.webrtc.security.SecurityValidator;
import com.ecoskiller.mcp.webrtc.model.*;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import org.json.*;

/**
 * EcoSkiller WebRTC MCP Server — Java
 *
 * Implements the Model Context Protocol (MCP) 2024-11-05
 * Transport: stdio (stdin/stdout JSON-RPC 2.0)
 *
 * 12 Secure Agents covering:
 *   - Session management (JWT, room lifecycle)
 *   - ICE/STUN/TURN NAT traversal
 *   - DTLS-SRTP key exchange & encryption
 *   - SDP negotiation
 *   - Media quality monitoring (WebRTC Stats API)
 *   - GD turn-based mute/unmute control
 *   - PSTN phone bridge (FreeSWITCH/mod_verto)
 *   - Audit & compliance logging (DPDPA 2023)
 */
public class McpWebRTCServer {

    private static final Logger LOG = Logger.getLogger(McpWebRTCServer.class.getName());
    public static final String MCP_VERSION = "2024-11-05";
    static final String SERVER_NAME = "mcp-webrtc-ecoskiller";
    static final String SERVER_VERSION = "1.0.0";

    private final SecurityValidator security;
    private final Map<String, BaseAgent> agents;

    public McpWebRTCServer() {
        this.security = new SecurityValidator();
        this.agents = new LinkedHashMap<>();
        registerAgents();
    }

    private void registerAgents() {
        agents.put("webrtc_session_create",       new WebRTCSessionCreateAgent(security));
        agents.put("webrtc_session_terminate",    new WebRTCSessionTerminateAgent(security));
        agents.put("webrtc_jwt_issue",            new WebRTCJwtIssueAgent(security));
        agents.put("webrtc_ice_negotiate",        new WebRTCIceNegotiateAgent(security));
        agents.put("webrtc_sdp_offer_answer",     new WebRTCSdpOfferAnswerAgent(security));
        agents.put("webrtc_dtls_srtp_status",     new WebRTCDtlsSrtpStatusAgent(security));
        agents.put("webrtc_media_quality",        new WebRTCMediaQualityAgent(security));
        agents.put("webrtc_gd_mute_control",      new WebRTCGdMuteControlAgent(security));
        agents.put("webrtc_turn_allocation",      new WebRTCTurnAllocationAgent(security));
        agents.put("webrtc_pstn_bridge",          new WebRTCPstnBridgeAgent(security));
        agents.put("webrtc_participant_stats",    new WebRTCParticipantStatsAgent(security));
        agents.put("webrtc_audit_log",            new WebRTCAuditLogAgent(security));
    }

    public void run() throws IOException {
        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  java.nio.charset.StandardCharsets.UTF_8));
        PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8), true);

        LOG.info("[MCP-WebRTC] Server started — " + agents.size() + " agents registered");

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            JSONObject response = handleRequest(line);
            if (response != null) {
                out.println(response.toString());
            }
        }
    }

    private JSONObject handleRequest(String rawJson) {
        JSONObject req;
        try {
            req = new JSONObject(rawJson);
        } catch (JSONException e) {
            return errorResponse(null, -32700, "Parse error: " + e.getMessage());
        }

        // Security: sanitise / validate input at entry point
        if (!security.isValidJsonRpcRequest(req)) {
            return errorResponse(req.opt("id"), -32600, "Invalid Request: failed security validation");
        }

        String method = req.optString("method", "");
        Object id     = req.opt("id");

        switch (method) {
            case "initialize":    return handleInitialize(id, req);
            case "ping":          return resultResponse(id, new JSONObject().put("status", "pong"));
            case "tools/list":    return handleToolsList(id);
            case "tools/call":    return handleToolCall(id, req);
            default:
                return errorResponse(id, -32601, "Method not found: " + method);
        }
    }

    // ─────────────────────────── handlers ──────────────────────────────

    private JSONObject handleInitialize(Object id, JSONObject req) {
        JSONObject result = new JSONObject()
            .put("protocolVersion", MCP_VERSION)
            .put("serverInfo", new JSONObject()
                .put("name", SERVER_NAME)
                .put("version", SERVER_VERSION))
            .put("capabilities", new JSONObject()
                .put("tools", new JSONObject()));
        return resultResponse(id, result);
    }

    private JSONObject handleToolsList(Object id) {
        JSONArray tools = new JSONArray();
        for (Map.Entry<String, BaseAgent> entry : agents.entrySet()) {
            tools.put(entry.getValue().getToolDefinition());
        }
        return resultResponse(id, new JSONObject().put("tools", tools));
    }

    private JSONObject handleToolCall(Object id, JSONObject req) {
        JSONObject params   = req.optJSONObject("params");
        if (params == null) return errorResponse(id, -32602, "Missing params");

        String toolName     = params.optString("name", "");
        JSONObject args     = params.optJSONObject("arguments");
        if (args == null) args = new JSONObject();

        // Security: validate tool name against whitelist
        if (!security.isAllowedToolName(toolName)) {
            return errorResponse(id, -32602, "Unknown or disallowed tool: " + toolName);
        }

        BaseAgent agent = agents.get(toolName);
        if (agent == null) {
            return errorResponse(id, -32602, "Tool not found: " + toolName);
        }

        try {
            AgentResult agentResult = agent.execute(args);
            JSONArray content = new JSONArray();
            content.put(new JSONObject()
                .put("type", "text")
                .put("text", agentResult.toJson().toString(2)));
            return resultResponse(id, new JSONObject().put("content", content));
        } catch (SecurityException se) {
            LOG.warning("[SECURITY] Tool=" + toolName + " blocked: " + se.getMessage());
            return errorResponse(id, -32001, "Security violation: " + se.getMessage());
        } catch (Exception e) {
            LOG.warning("[ERROR] Tool=" + toolName + " failed: " + e.getMessage());
            return errorResponse(id, -32000, "Agent error: " + e.getMessage());
        }
    }

    // ─────────────────────────── helpers ───────────────────────────────

    static JSONObject resultResponse(Object id, Object result) {
        return new JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id == null ? JSONObject.NULL : id)
            .put("result", result);
    }

    static JSONObject errorResponse(Object id, int code, String message) {
        return new JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id == null ? JSONObject.NULL : id)
            .put("error", new JSONObject()
                .put("code", code)
                .put("message", message));
    }

    public static void main(String[] args) {
        // Suppress JUL noise on stderr (keep stdout clean for MCP)
        LogManager.getLogManager().reset();
        Logger root = Logger.getLogger("");
        root.addHandler(new StreamHandler(System.err, new SimpleFormatter()));
        root.setLevel(Level.INFO);

        try {
            new McpWebRTCServer().run();
        } catch (IOException e) {
            System.err.println("[FATAL] " + e.getMessage());
            System.exit(1);
        }
    }
}
