package io.ecoskiller.recruiter.agents;

import java.util.LinkedHashMap;
import java.util.Map;
import io.ecoskiller.recruiter.config.ServerConfig;
import io.ecoskiller.recruiter.security.AuditLogger;

public class AgentFactory {
    public static Map<String, AgentHandler> createAllAgents(ServerConfig config, AuditLogger audit) {
        Map<String, AgentHandler> agents = new LinkedHashMap<>();
        
        // Account & Profile
        agents.put("recruiter_account_onboard",       new RecruiterAccountOnboardAgent(config, audit));
        agents.put("recruiter_profile_get",           new RecruiterProfileGetAgent(config, audit));
        agents.put("recruiter_profile_update",        new RecruiterProfileUpdateAgent(config, audit));
        agents.put("recruiter_dashboard_get",         new RecruiterDashboardGetAgent(config, audit));
        agents.put("recruiter_applications_list",     new RecruiterApplicationsListAgent(config, audit));
        
        // Candidates & Notifications
        agents.put("recruiter_candidate_save",        new RecruiterCandidateSaveAgent(config, audit));
        agents.put("recruiter_saved_candidates_list", new RecruiterSavedCandidatesListAgent(config, audit));
        agents.put("recruiter_notifications_get",     new RecruiterNotificationsGetAgent(config, audit));
        agents.put("recruiter_notification_mark_read",new RecruiterNotificationMarkReadAgent(config, audit));
        agents.put("recruiter_subscription_get",      new RecruiterSubscriptionGetAgent(config, audit));
        
        // Team & Subscription
        agents.put("recruiter_team_get",              new RecruiterTeamGetAgent(config, audit));
        agents.put("recruiter_team_invite",           new RecruiterTeamInviteAgent(config, audit));
        agents.put("recruiter_team_remove",           new RecruiterTeamRemoveAgent(config, audit));
        agents.put("recruiter_subscription_upgrade",  new RecruiterSubscriptionUpgradeAgent(config, audit));
        agents.put("recruiter_subscription_cancel",   new RecruiterSubscriptionCancelAgent(config, audit));
        
        // Analytics, Webhooks, Compliance
        agents.put("recruiter_analytics_get",         new RecruiterAnalyticsGetAgent(config, audit));
        agents.put("recruiter_webhook_register",      new RecruiterWebhookRegisterAgent(config, audit));
        agents.put("recruiter_audit_log_query",       new RecruiterAuditLogQueryAgent(config, audit));
        
        return agents;
    }
}
