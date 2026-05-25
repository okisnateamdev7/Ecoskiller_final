package com.ecoskiller.mcp.redis.security;

import com.ecoskiller.mcp.redis.config.ServerConfig;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Append-only audit log for all MCP tool invocations.
 *
 * Each log entry is a JSON line:
 *   {"ts":"...","event":"TOOL_CALL","tool":"...","tenant":"...","status":"OK|ERROR|SECURITY"}
 *
 * Sensitive values (OTP, password, lock_value) are NEVER written to the audit log.
 * The audit log is written to AUDIT_LOG_PATH (env var, default /var/log/redis-mcp-audit.log).
 */
public class AuditLogger {

    private static final Logger LOGGER = Logger.getLogger(AuditLogger.class.getName());

    /** Fields to redact from audit entries */
    private static final java.util.Set<String> REDACTED_FIELDS = java.util.Set.of(
            "otp_value", "password", "lock_value", "value", "token"
    );

    private PrintWriter writer;

    public AuditLogger() {
        try {
            FileOutputStream fos = new FileOutputStream(ServerConfig.AUDIT_LOG_PATH, true);
            writer = new PrintWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8), true);
            LOGGER.info("[Audit] Logging to " + ServerConfig.AUDIT_LOG_PATH);
        } catch (IOException e) {
            LOGGER.warning("[Audit] Cannot open audit log at " + ServerConfig.AUDIT_LOG_PATH
                    + " — falling back to stderr. Reason: " + e.getMessage());
            writer = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        }
    }

    public void logRequest(String method, JSONObject req) {
        write("MCP_REQUEST", method, null, null, null);
    }

    public void logSuccess(String tool, JSONObject args) {
        write("TOOL_CALL", tool, tenant(args), "OK", null);
    }

    public void logError(String tool, JSONObject args, String msg) {
        write("TOOL_CALL", tool, tenant(args), "ERROR", truncate(msg));
    }

    public void logSecurityViolation(String tool, JSONObject args, String reason) {
        write("SECURITY_VIOLATION", tool, tenant(args), "BLOCKED", truncate(reason));
    }

    // ─── Internal ────────────────────────────────────────────────────────────────

    private void write(String event, String tool, String tenant, String status, String detail) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("ts",     Instant.now().toString());
            entry.put("event",  event);
            entry.put("tool",   tool);
            entry.put("env",    ServerConfig.ENV);
            if (tenant != null) entry.put("tenant", tenant);
            if (status != null) entry.put("status", status);
            if (detail != null) entry.put("detail", detail);
            synchronized (this) { writer.println(entry); }
        } catch (Exception e) {
            LOGGER.warning("[Audit] Write failed: " + e.getMessage());
        }
    }

    private String tenant(JSONObject args) {
        return args != null ? args.optString("tenant_id", "unknown") : "unknown";
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
