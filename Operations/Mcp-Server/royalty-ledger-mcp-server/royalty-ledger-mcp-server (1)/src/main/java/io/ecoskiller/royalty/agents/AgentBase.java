package io.ecoskiller.royalty.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.royalty.config.ServerConfig;
import io.ecoskiller.royalty.security.AuditLogger;

import java.time.Instant;
import java.util.UUID;


abstract class BaseAgent implements AgentHandler {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final ServerConfig config;
    protected final AuditLogger audit;

    protected BaseAgent(ServerConfig c, AuditLogger a) { config = c; audit = a; }

    protected ObjectNode ok(String msg) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "success"); n.put("message", msg);
        n.put("timestamp", Instant.now().toString()); return n;
    }

    protected ObjectNode errNode(String msg) {
        ObjectNode n = mapper.createObjectNode();
        n.put("status", "error"); n.put("message", msg);
        n.put("timestamp", Instant.now().toString()); return n;
    }

    protected String genId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-","").substring(0,12);
    }

    protected String str(JsonNode n, String f, String d) {
        return (n!=null && n.has(f) && !n.get(f).isNull()) ? n.get(f).asText() : d;
    }
    protected int num(JsonNode n, String f, int d) { return (n!=null && n.has(f)) ? n.get(f).asInt(d) : d; }
    protected double dbl(JsonNode n, String f, double d) { return (n!=null && n.has(f)) ? n.get(f).asDouble(d) : d; }
    protected boolean bool(JsonNode n, String f, boolean d) { return (n!=null && n.has(f)) ? n.get(f).asBoolean(d) : d; }

    protected void addProp(ObjectNode p, String n, String desc) {
        p.putObject(n).put("type","string").put("description", desc);
    }
    protected void addNumProp(ObjectNode p, String n, String desc) {
        p.putObject(n).put("type","number").put("description", desc);
    }
    protected void addBoolProp(ObjectNode p, String n, String desc) {
        p.putObject(n).put("type","boolean").put("description", desc);
    }
    protected void addIntProp(ObjectNode p, String n, String desc) {
        p.putObject(n).put("type","integer").put("description", desc);
    }
    protected void addAuth(ObjectNode p) {
        addProp(p, "jwt_token", "Keycloak JWT Bearer token. Must contain CREATOR or ADMIN role claim.");
    }

    protected ObjectNode tool(String name, String desc) {
        ObjectNode t = mapper.createObjectNode();
        t.put("name", name); t.put("description", desc); return t;
    }

    protected ObjectNode schema(ObjectNode t) {
        ObjectNode s = t.putObject("inputSchema");
        s.put("type","object"); return s;
    }
}
