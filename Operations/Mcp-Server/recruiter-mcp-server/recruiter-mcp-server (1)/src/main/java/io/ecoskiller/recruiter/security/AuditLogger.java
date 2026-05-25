package io.ecoskiller.recruiter.security;

import io.ecoskiller.recruiter.config.ServerConfig;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

class AuditLoggerState {
    static final ConcurrentLinkedDeque<AuditLogger.AuditEvent> BUFFER = new ConcurrentLinkedDeque<>();
}

public class AuditLogger {
    private static final Logger LOG = Logger.getLogger(AuditLogger.class.getName());
    private static final int BUFFER_SIZE = 2000;
    private final boolean enabled;

    public AuditLogger(ServerConfig c) { this.enabled = c.isAuditEnabled(); }

    public void info(String event, String actor, String detail)  { log("INFO",  event, actor, detail); }
    public void warn(String event, String actor, String detail)  { log("WARN",  event, actor, detail); }
    public void error(String event, String actor, String detail) { log("ERROR", event, actor, detail); }

    private void log(String level, String event, String actor, String detail) {
        if (!enabled) return;
        String safeActor  = InputSanitizer.sanitizeForLog(actor);
        String safeDetail = InputSanitizer.sanitizeForLog(detail);
        String safeEvent  = InputSanitizer.sanitizeForLog(event);
        String msg = String.format("[AUDIT] %s | %s | actor=%s | %s | ts=%s",
            level, safeEvent, safeActor, safeDetail, Instant.now());
        System.err.println(msg);  // stderr only; stdout reserved for JSON-RPC
        AuditEvent e = new AuditEvent(level, event, actor, detail, Instant.now().toString());
        AuditLoggerState.BUFFER.addLast(e);
        while (AuditLoggerState.BUFFER.size() > BUFFER_SIZE) AuditLoggerState.BUFFER.pollFirst();
    }

    public List<AuditEvent> query(String eventFilter, String actorFilter, int limit) {
        List<AuditEvent> results = new ArrayList<>();
        for (AuditEvent e : new ArrayDeque<>(AuditLoggerState.BUFFER)) {
            boolean matchEvent = "ALL".equals(eventFilter) || e.eventType().equalsIgnoreCase(eventFilter);
            boolean matchActor = actorFilter == null || actorFilter.isBlank()
                || e.actor().contains(actorFilter);
            if (matchEvent && matchActor) {
                results.add(e);
                if (results.size() >= limit) break;
            }
        }
        return results;
    }

    public record AuditEvent(String level, String eventType, String actor, String detail, String timestamp) {}
}
