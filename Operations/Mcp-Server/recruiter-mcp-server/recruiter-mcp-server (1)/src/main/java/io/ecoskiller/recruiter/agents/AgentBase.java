package io.ecoskiller.recruiter.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ecoskiller.recruiter.config.ServerConfig;
import io.ecoskiller.recruiter.security.AuditLogger;

import java.time.Instant;
import java.util.UUID;



// ─────────────────────────────────────────────────────────────────────────────

abstract class BaseAgent implements AgentHandler {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ServerConfig config;
    protected final AuditLogger audit;

    protected BaseAgent(ServerConfig config, AuditLogger audit) {
        this.config = config;
        this.audit  = audit;
    }

    // ── Common response builders ─────────────────────────────────────────────

    protected ObjectNode ok(String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "success");
        n.put("message", message);
        n.put("timestamp", Instant.now().toString());
        return n;
    }

    protected ObjectNode err(String message) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "error");
        n.put("message", message);
        n.put("timestamp", Instant.now().toString());
        return n;
    }

    protected String eventId()   { return "evt-" + UUID.randomUUID().toString().replace("-","").substring(0,12); }
    protected String inviteToken() { return "inv-" + UUID.randomUUID().toString().replace("-","").substring(0,16); }
    protected String webhookId()  { return "wh-"  + UUID.randomUUID().toString().replace("-","").substring(0,12); }

    // ── Safe getters from JsonNode ───────────────────────────────────────────

    protected String str(JsonNode n, String f, String def) {
        return (n != null && n.has(f) && !n.get(f).isNull()) ? n.get(f).asText() : def;
    }
    protected int num(JsonNode n, String f, int def) {
        return (n != null && n.has(f)) ? n.get(f).asInt(def) : def;
    }
    protected boolean bool(JsonNode n, String f, boolean def) {
        return (n != null && n.has(f)) ? n.get(f).asBoolean(def) : def;
    }

    // ── Tool schema helpers ──────────────────────────────────────────────────

    protected void addProp(ObjectNode props, String name, String desc) {
        props.putObject(name).put("type", "string").put("description", desc);
    }
    protected void addIntProp(ObjectNode props, String name, String desc) {
        props.putObject(name).put("type", "integer").put("description", desc);
    }
    protected void addBoolProp(ObjectNode props, String name, String desc) {
        props.putObject(name).put("type", "boolean").put("description", desc);
    }

    /** Every agent requires jwt_token for auth. */
    protected void addAuthProp(ObjectNode props) {
        addProp(props, "jwt_token",
            "Keycloak JWT Bearer token (RECRUITER or ADMIN role). " +
            "Claims must include: sub (recruiter_id), roles, company_id, tenant_id.");
    }

    protected ObjectNode tool(String name, String description) {
        ObjectNode t = mapper.createObjectNode();
        t.put("name", name);
        t.put("description", description);
        return t;
    }

    protected ObjectNode schema(ObjectNode tool) {
        ObjectNode s = tool.putObject("inputSchema");
        s.put("type", "object");
        return s;
    }
}
