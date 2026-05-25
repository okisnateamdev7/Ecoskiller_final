package io.ecoskiller.prosody.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ecoskiller.prosody.config.ServerConfig;
import io.ecoskiller.prosody.security.AuditLogger;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all Prosody agents.
 * Provides JSON helpers, config, audit logger, and common response patterns.
 */
public abstract class BaseAgent implements AgentHandler {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ServerConfig config;
    protected final AuditLogger auditLogger;

    protected BaseAgent(ServerConfig config, AuditLogger auditLogger) {
        this.config = config;
        this.auditLogger = auditLogger;
    }

    // ── Common response builders ─────────────────────────────────────────────

    protected ObjectNode successResult(String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "success");
        n.put("message", message);
        n.put("timestamp", Instant.now().toString());
        return n;
    }

    protected ObjectNode errorResult(String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "error");
        n.put("message", message);
        n.put("timestamp", Instant.now().toString());
        return n;
    }

    protected String generateEventId() {
        return "evt-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected String generateRoomJid(String roomId) {
        return roomId + "@conference." + config.getXmppDomain();
    }

    protected String generateUserJid(String userId) {
        return userId + "@" + config.getXmppDomain();
    }

    /** Build a minimal JSON Schema for tool input */
    protected ObjectNode buildSchema(String... requiredFields) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = mapper.createArrayNode();
        for (String field : requiredFields) {
            properties.putObject(field).put("type", "string");
            required.add(field);
        }
        schema.set("required", required);
        return schema;
    }

    /** Safely get string field from args, never null */
    protected String getString(com.fasterxml.jackson.databind.JsonNode args, String field, String defaultVal) {
        return args != null && args.has(field) && !args.get(field).isNull()
            ? args.get(field).asText() : defaultVal;
    }

    protected int getInt(com.fasterxml.jackson.databind.JsonNode args, String field, int defaultVal) {
        return args != null && args.has(field) ? args.get(field).asInt(defaultVal) : defaultVal;
    }

    protected boolean getBool(com.fasterxml.jackson.databind.JsonNode args, String field, boolean defaultVal) {
        return args != null && args.has(field) ? args.get(field).asBoolean(defaultVal) : defaultVal;
    }
}
