package io.ecoskiller.royalty.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.royalty.agents.*;
import io.ecoskiller.royalty.config.ServerConfig;
import io.ecoskiller.royalty.security.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.*;

/**
 * Ecoskiller Royalty Ledger MCP Server (CAT-ROYALTY-LEDGER)
 *
 * Secure Java MCP Server — JSON-RPC 2.0 over stdio.
 * 16 agents covering the full royalty-ledger-service:
 *   IP registration, royalty accrual (real-time), ledger accounting,
 *   payout management, tax compliance, fraud detection,
 *   dispute resolution, split management, creator tier, and audit.
 *
 * Security:
 *   - JWT HMAC-SHA256 validation + 5-min cache
 *   - Strict input validation (content hash format, IP types, account types)
 *   - Self-submission fraud detection at agent level
 *   - Rate limiting (token bucket)
 *   - 64KB payload cap + null byte detection
 *   - SOC 2 / DPDPA 2023 audit logging to stderr (ELK-ready)
 *   - All secrets from env vars / Kubernetes Secrets
 */
public class RoyaltyLedgerMcpServer {

    private static final Logger LOG = Logger.getLogger(RoyaltyLedgerMcpServer.class.getName());
    private static final String MCP_VERSION    = "2024-11-05";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String SERVER_NAME    = "mcp-royalty-ledger-ecoskiller";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AgentHandler> agents = new LinkedHashMap<>();
    private final RateLimiter rateLimiter;
    private final RequestValidator validator;
    private final AuditLogger audit;
    private final ServerConfig config;

    public RoyaltyLedgerMcpServer() {
        this.config      = ServerConfig.load();
        this.rateLimiter = new RateLimiter(config);
        this.validator   = new RequestValidator();
        this.audit       = new AuditLogger(config);
        registerAgents();
        LOG.info("RoyaltyLedgerMcpServer initialised — " + agents.size() + " agents loaded.");
    }

    private void registerAgents() {
        agents.putAll(AgentFactory.createAllAgents(config, audit));
    }

    // ── Main loop ────────────────────────────────────────────────────────────

    public void run() throws IOException {
        LOG.info("RoyaltyLedgerMcpServer listening on stdin/stdout (JSON-RPC 2.0 / MCP 2024-11-05)");
        BufferedReader in  = new BufferedReader(new InputStreamReader(System.in,  StandardCharsets.UTF_8));
        PrintWriter    out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) { String r = dispatch(line); if (r != null) out.println(r); }
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
        JsonNode params = req.has("params")  ? req.get("params") : mapper.createObjectNode();

        String key = clientKey(params);
        if (!rateLimiter.allow(key)) {
            audit.warn("RATE_LIMIT", key, "method=" + method);
            return err(id, -32000, "Rate limit exceeded.");
        }

        try {
            return switch (method) {
                case "initialize"  -> handleInit(id);
                case "ping"        -> ok(id, mapper.createObjectNode().put("status","pong"));
                case "tools/list"  -> handleList(id);
                case "tools/call"  -> handleCall(id, params);
                default            -> err(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Dispatch error: " + method, e);
            return err(id, -32603, "Internal error: " + e.getMessage());
        }
    }

    private String handleInit(Object id) throws Exception {
        audit.info("INITIALIZE", "server", "MCP handshake");
        ObjectNode res = mapper.createObjectNode();
        res.put("protocolVersion", MCP_VERSION);
        ObjectNode si = res.putObject("serverInfo");
        si.put("name",        SERVER_NAME);
        si.put("version",     SERVER_VERSION);
        si.put("description", "Ecoskiller Royalty Ledger MCP — IP Rights & Royalty Accrual Tracking");
        res.putObject("capabilities").putObject("tools");
        return ok(id, res);
    }

    private String handleList(Object id) throws Exception {
        ArrayNode tools = mapper.createArrayNode();
        for (AgentHandler h : agents.values()) tools.add(h.getToolDefinition());
        ObjectNode res = mapper.createObjectNode();
        res.set("tools", tools);
        return ok(id, res);
    }

    private String handleCall(Object id, JsonNode params) throws Exception {
        if (!params.has("name")) return err(id, -32602, "Missing tool name");

        String toolName = params.get("name").asText();
        JsonNode args   = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        if (!validator.isValidToolName(toolName)) {
            audit.warn("INVALID_TOOL", "unknown", toolName);
            return err(id, -32602, "Invalid tool name: " + toolName);
        }

        AgentHandler agent = agents.get(toolName);
        if (agent == null) return err(id, -32601, "Unknown tool: " + toolName);

        List<String> errs = validator.validate(toolName, args);
        if (!errs.isEmpty()) {
            audit.warn("VALIDATION_FAIL", "unknown", toolName + ": " + errs);
            return err(id, -32602, "Validation errors: " + errs);
        }

        audit.info("TOOL_CALL", clientKey(args), toolName);

        try {
            JsonNode result = agent.execute(args);
            ObjectNode res = mapper.createObjectNode();
            res.putArray("content").addObject().put("type","text").put("text", mapper.writeValueAsString(result));
            res.put("isError", false);
            return ok(id, res);
        } catch (SecurityException se) {
            audit.error("SECURITY_VIOLATION", clientKey(args), toolName + ": " + se.getMessage());
            ObjectNode res = mapper.createObjectNode();
            res.putArray("content").addObject().put("type","text").put("text", "{\"error\":\"" + esc(se.getMessage()) + "\"}");
            res.put("isError", true);
            return ok(id, res);
        } catch (Exception e) {
            audit.error("AGENT_ERROR", clientKey(args), toolName + ": " + e.getMessage());
            ObjectNode res = mapper.createObjectNode();
            res.putArray("content").addObject().put("type","text").put("text", "{\"error\":\"" + esc(e.getMessage()) + "\"}");
            res.put("isError", true);
            return ok(id, res);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String ok(Object id, JsonNode result) throws Exception {
        ObjectNode r = mapper.createObjectNode();
        r.put("jsonrpc","2.0"); r.set("id", idNode(id)); r.set("result", result);
        return mapper.writeValueAsString(r);
    }

    private String err(Object id, int code, String msg) {
        try {
            ObjectNode r = mapper.createObjectNode();
            r.put("jsonrpc","2.0"); r.set("id", idNode(id));
            r.putObject("error").put("code",code).put("message",msg);
            return mapper.writeValueAsString(r);
        } catch (Exception e) {
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
            return t.length() > 16 ? t.substring(0,16) + "..." : t;
        }
        return "anonymous";
    }

    private String esc(String s) {
        if (s == null) return "null";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        setupLogging();
        LOG.info("Starting Ecoskiller Royalty Ledger MCP Server v" + SERVER_VERSION);
        new RoyaltyLedgerMcpServer().run();
    }

    private static void setupLogging() {
        Logger root = Logger.getLogger(""); root.setLevel(Level.INFO);
        for (Handler h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler h = new ConsoleHandler();
        h.setLevel(Level.ALL); h.setFormatter(new SimpleFormatter());
        root.addHandler(h);
    }
}
