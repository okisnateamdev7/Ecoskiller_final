package io.ecoskiller.recruiter.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.recruiter.agents.*;
import io.ecoskiller.recruiter.config.ServerConfig;
import io.ecoskiller.recruiter.security.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;

/**
 * Ecoskiller Recruiter MCP Server (CAT-RECRUITER)
 *
 * Secure Java MCP Server — JSON-RPC 2.0 over stdio.
 * 18 agents covering the full recruiter lifecycle on the Ecoskiller platform.
 *
 * Security layers:
 *   - JWT validation (HMAC-SHA256) on every tool call
 *   - Input sanitization (strict allowlist regex, SQL/HTML injection prevention)
 *   - SSRF protection on webhook URLs
 *   - Token-bucket rate limiting (per client key)
 *   - Payload size cap (64KB)
 *   - Null byte injection detection
 *   - Audit logging (SOC 2 / DPDPA 2023 compliant)
 *   - All secrets from env vars / Kubernetes Secrets
 */
public class RecruiterMcpServer {

    private static final Logger LOG = Logger.getLogger(RecruiterMcpServer.class.getName());
    private static final String MCP_VERSION    = "2024-11-05";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String SERVER_NAME    = "mcp-recruiter-ecoskiller";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AgentHandler> agents = new LinkedHashMap<>();
    private final RateLimiter rateLimiter;
    private final RequestValidator validator;
    private final AuditLogger audit;
    private final ServerConfig config;

    // ── Constructor: register all 18 agents ─────────────────────────────────

    public RecruiterMcpServer() {
        this.config      = ServerConfig.load();
        this.rateLimiter = new RateLimiter(config);
        this.validator   = new RequestValidator();
        this.audit       = new AuditLogger(config);
        registerAgents();
        LOG.info("RecruiterMcpServer initialised — " + agents.size() + " agents loaded.");
    }

    private void registerAgents() {
        agents.putAll(AgentFactory.createAllAgents(config, audit));
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    public void run() throws IOException {
        LOG.info("RecruiterMcpServer listening on stdin/stdout (JSON-RPC 2.0 / MCP 2024-11-05)");
        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  StandardCharsets.UTF_8));
        PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                String resp = dispatch(line);
                if (resp != null) out.println(resp);
            }
        }
    }

    // ── JSON-RPC dispatcher ──────────────────────────────────────────────────

    public String dispatch(String raw) {
        JsonNode req;
        try { req = mapper.readTree(raw); }
        catch (Exception e) { return err(null, -32700, "Parse error: " + e.getMessage()); }

        JsonNode idNode = req.get("id");
        Object   id     = idNode == null ? null : (idNode.isNumber() ? idNode.asLong() : idNode.asText());
        String   method = req.has("method") ? req.get("method").asText() : "";
        JsonNode params = req.has("params") ? req.get("params") : mapper.createObjectNode();

        // Rate limit — keyed by token prefix or "anonymous"
        String clientKey = clientKey(params);
        if (!rateLimiter.allow(clientKey)) {
            audit.warn("RATE_LIMIT_HIT", clientKey, "method=" + method);
            return err(id, -32000, "Rate limit exceeded. Please slow down.");
        }

        try {
            return switch (method) {
                case "initialize"  -> handleInitialize(id, params);
                case "ping"        -> handlePing(id);
                case "tools/list"  -> handleToolsList(id);
                case "tools/call"  -> handleToolsCall(id, params);
                default            -> err(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Dispatch error: " + method, e);
            return err(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ── MCP method handlers ──────────────────────────────────────────────────

    private String handleInitialize(Object id, JsonNode params) throws Exception {
        audit.info("INITIALIZE", "server", "MCP handshake");
        ObjectNode res = mapper.createObjectNode();
        res.put("protocolVersion", MCP_VERSION);
        ObjectNode si = res.putObject("serverInfo");
        si.put("name",        SERVER_NAME);
        si.put("version",     SERVER_VERSION);
        si.put("description", "Ecoskiller Recruiter MCP — Talent Acquisition & Hiring Management");
        res.putObject("capabilities").putObject("tools");
        return ok(id, res);
    }

    private String handlePing(Object id) throws Exception {
        return ok(id, mapper.createObjectNode().put("status", "pong"));
    }

    private String handleToolsList(Object id) throws Exception {
        ArrayNode tools = mapper.createArrayNode();
        for (AgentHandler h : agents.values()) tools.add(h.getToolDefinition());
        ObjectNode res = mapper.createObjectNode();
        res.set("tools", tools);
        return ok(id, res);
    }

    private String handleToolsCall(Object id, JsonNode params) throws Exception {
        if (!params.has("name"))
            return err(id, -32602, "Missing tool name");

        String   toolName = params.get("name").asText();
        JsonNode args     = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        if (!validator.isValidToolName(toolName)) {
            audit.warn("INVALID_TOOL_NAME", "unknown", toolName);
            return err(id, -32602, "Invalid tool name: " + toolName);
        }

        AgentHandler agent = agents.get(toolName);
        if (agent == null)
            return err(id, -32601, "Unknown tool: " + toolName);

        List<String> validationErrors = validator.validate(toolName, args);
        if (!validationErrors.isEmpty()) {
            audit.warn("VALIDATION_FAIL", "unknown", toolName + ": " + validationErrors);
            return err(id, -32602, "Validation errors: " + validationErrors);
        }

        audit.info("TOOL_CALL", clientKey(args), toolName);

        try {
            JsonNode result = agent.execute(args);
            ObjectNode res  = mapper.createObjectNode();
            ArrayNode  content = res.putArray("content");
            content.addObject().put("type", "text").put("text", mapper.writeValueAsString(result));
            res.put("isError", false);
            return ok(id, res);
        } catch (SecurityException se) {
            audit.error("SECURITY_VIOLATION", clientKey(args), toolName + ": " + se.getMessage());
            ObjectNode res = mapper.createObjectNode();
            res.putArray("content").addObject().put("type","text")
                .put("text", "{\"error\":\"" + escapeJson(se.getMessage()) + "\"}");
            res.put("isError", true);
            return ok(id, res);
        } catch (Exception e) {
            audit.error("AGENT_ERROR", clientKey(args), toolName + ": " + e.getMessage());
            ObjectNode res = mapper.createObjectNode();
            res.putArray("content").addObject().put("type","text")
                .put("text", "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            res.put("isError", true);
            return ok(id, res);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String ok(Object id, JsonNode result) throws Exception {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", idNode(id));
        r.set("result", result);
        return mapper.writeValueAsString(r);
    }

    private String err(Object id, int code, String msg) {
        try {
            ObjectNode r = mapper.createObjectNode();
            r.put("jsonrpc", "2.0");
            r.set("id", idNode(id));
            ObjectNode e = r.putObject("error");
            e.put("code", code); e.put("message", msg);
            return mapper.writeValueAsString(r);
        } catch (Exception ex) {
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Serialisation error\"}}";
        }
    }

    private JsonNode idNode(Object id) {
        if (id == null) return mapper.nullNode();
        if (id instanceof Long l) return mapper.getNodeFactory().numberNode(l);
        return mapper.getNodeFactory().textNode(id.toString());
    }

    private String clientKey(JsonNode p) {
        if (p != null && p.has("jwt_token")) {
            String t = p.get("jwt_token").asText();
            return t.length() > 16 ? t.substring(0, 16) + "..." : t;
        }
        return "anonymous";
    }

    private String escapeJson(String s) {
        if (s == null) return "null";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r");
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        setupLogging();
        LOG.info("Starting Ecoskiller Recruiter MCP Server v" + SERVER_VERSION);
        new RecruiterMcpServer().run();
    }

    private static void setupLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler h = new ConsoleHandler();
        // h.setStream(System.err);  // logs to stderr; stdout reserved for JSON-RPC
        h.setLevel(Level.ALL);
        h.setFormatter(new SimpleFormatter());
        root.addHandler(h);
    }
}
