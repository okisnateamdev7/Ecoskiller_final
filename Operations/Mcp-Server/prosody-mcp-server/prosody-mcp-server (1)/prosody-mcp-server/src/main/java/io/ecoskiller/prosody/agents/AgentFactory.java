package io.ecoskiller.prosody.agents;

import io.ecoskiller.prosody.config.ServerConfig;
import io.ecoskiller.prosody.security.AuditLogger;
import io.ecoskiller.prosody.security.RateLimiter;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Factory for creating all Prosody agents within their package package-private context.
 */
public class AgentFactory {
    public static Map<String, AgentHandler> createAllAgents(ServerConfig config, AuditLogger auditLogger, RateLimiter rateLimiter) {
        Map<String, AgentHandler> agents = new LinkedHashMap<>();
        agents.put("xmpp_room_create",          new XmppRoomCreateAgent(config, auditLogger));
        agents.put("xmpp_room_close",           new XmppRoomCloseAgent(config, auditLogger));
        agents.put("xmpp_room_query",           new XmppRoomQueryAgent(config, auditLogger));
        agents.put("xmpp_participant_join",     new XmppParticipantJoinAgent(config, auditLogger));
        agents.put("xmpp_participant_leave",    new XmppParticipantLeaveAgent(config, auditLogger));
        agents.put("xmpp_roster_get",           new XmppRosterGetAgent(config, auditLogger));
        agents.put("xmpp_presence_update",      new XmppPresenceUpdateAgent(config, auditLogger));
        agents.put("xmpp_signaling_relay",      new XmppSignalingRelayAgent(config, auditLogger));
        agents.put("xmpp_jwt_validate",         new XmppJwtValidateAgent(config, auditLogger));
        agents.put("xmpp_connection_health",    new XmppConnectionHealthAgent(config, auditLogger));
        agents.put("xmpp_metrics_get",          new XmppMetricsGetAgent(config, auditLogger));
        agents.put("xmpp_rate_limit_control",   new XmppRateLimitControlAgent(config, auditLogger, rateLimiter));
        agents.put("xmpp_kafka_event_emit",     new XmppKafkaEventEmitAgent(config, auditLogger));
        agents.put("xmpp_audit_log_query",      new XmppAuditLogQueryAgent(config, auditLogger));
        return agents;
    }
}
