package io.ecoskiller.royalty.security;

import io.ecoskiller.royalty.config.ServerConfig;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

public class AuditLogger {
    private static final Logger LOG = Logger.getLogger(AuditLogger.class.getName());
    private static final int BUFFER = 2000;
    private final boolean enabled;
    private final ConcurrentLinkedDeque<AuditEvent> buf = new ConcurrentLinkedDeque<>();

    public AuditLogger(ServerConfig c) { enabled = c.isAuditEnabled(); }

    public void info(String event, String actor, String detail)  { log("INFO",  event, actor, detail); }
    public void warn(String event, String actor, String detail)  { log("WARN",  event, actor, detail); }
    public void error(String event, String actor, String detail) { log("ERROR", event, actor, detail); }

    private void log(String level, String event, String actor, String detail) {
        if (!enabled) return;
        String msg = String.format("[AUDIT] %s | %s | actor=%s | %s | ts=%s",
            level, InputSanitizer.sanitizeForLog(event),
            InputSanitizer.sanitizeForLog(actor),
            InputSanitizer.sanitizeForLog(detail), Instant.now());
        System.err.println(msg);
        buf.addLast(new AuditEvent(level, event, actor, detail, Instant.now().toString()));
        while (buf.size() > BUFFER) buf.pollFirst();
    }

    public List<AuditEvent> query(String eventFilter, int limit) {
        List<AuditEvent> r = new ArrayList<>();
        for (AuditEvent e : new ArrayDeque<>(buf)) {
            if ("ALL".equals(eventFilter) || e.eventType().equalsIgnoreCase(eventFilter)) {
                r.add(e); if (r.size() >= limit) break;
            }
        }
        return r;
    }

    public record AuditEvent(String level, String eventType, String actor, String detail, String timestamp) {}
}
