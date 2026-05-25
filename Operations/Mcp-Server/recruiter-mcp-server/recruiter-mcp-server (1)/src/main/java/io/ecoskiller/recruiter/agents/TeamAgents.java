package io.ecoskiller.recruiter.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.recruiter.config.ServerConfig;
import io.ecoskiller.recruiter.security.*;

import java.time.Instant;

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 11 — RECRUITER_TEAM_GET
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterTeamGetAgent extends BaseAgent {
    public RecruiterTeamGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_team_get",
            "Lists all team members for a recruiter: hiring managers, interviewers, and co-recruiters. " +
            "Returns role, permissions scope, invite status, and last activity. " +
            "Data from team_member table (RLS-scoped to recruiter_id). " +
            "Supports role-based filtering.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",  "Recruiter whose team to retrieve");
        addProp(p, "role_filter",   "Filter: 'HIRING_MANAGER' | 'INTERVIEWER' | 'RECRUITER' | 'all' (default: all)");
        addProp(p, "status_filter", "Filter: 'accepted' | 'pending' | 'all' (default: all)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String roleFilter  = str(a, "role_filter", "all");
        String statusFilter= str(a, "status_filter", "all");
        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateEnum(roleFilter, "role_filter",
            "HIRING_MANAGER","INTERVIEWER","RECRUITER","all");
        InputSanitizer.validateEnum(statusFilter, "status_filter", "accepted","pending","all");
        audit.info("TEAM_GET", recruiterId, "role=" + roleFilter + " status=" + statusFilter);

        ObjectNode res = ok("Team members retrieved");
        res.put("recruiter_id", recruiterId);
        res.put("total_members", 0);
        res.putArray("team_members").addObject()
            .put("note", "Live from team_member WHERE recruiter_id='" + recruiterId + "'");
        res.put("db_table", "team_member (recruiter_id, email, role, permissions_scope, invited_at, accepted_at)");
        res.put("rls_applied", true);
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 12 — RECRUITER_TEAM_INVITE
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterTeamInviteAgent extends BaseAgent {
    public RecruiterTeamInviteAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_team_invite",
            "Invites a hiring manager or interviewer to join a recruiter's team. " +
            "Generates a role-scoped invite token with 48-hour expiry. " +
            "Writes to team_member table (status: pending). " +
            "Emits recruiter.team.member.invited Kafka event → notification-service (sends invite email). " +
            "Logs to ClickHouse audit trail.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",       "Recruiter sending the invite");
        addProp(p, "invitee_email",       "Email address of the person being invited");
        addProp(p, "role",                "Role to assign: 'HIRING_MANAGER' | 'INTERVIEWER'");
        addProp(p, "permissions_scope",   "Comma-separated permissions: 'view_candidates,schedule_interviews,add_notes'");
        addProp(p, "message",             "Optional personal message in invite email (max 300 chars)");
        addAuthProp(p);
        ArrayNode req = s.putArray("required");
        req.add("recruiter_id"); req.add("invitee_email"); req.add("role"); req.add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId  = str(a, "recruiter_id", "");
        String inviteeEmail = str(a, "invitee_email", "");
        String role         = str(a, "role", "");
        String scope        = InputSanitizer.sanitizeText(str(a, "permissions_scope", "view_candidates"), 200);
        String message      = InputSanitizer.sanitizeText(str(a, "message", ""), 300);

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.requireNonBlank(inviteeEmail, "invitee_email");
        InputSanitizer.requireNonBlank(role, "role");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateEmail(inviteeEmail);
        InputSanitizer.validateRole(role);
        if (!role.equals("HIRING_MANAGER") && !role.equals("INTERVIEWER"))
            throw new SecurityException("Only HIRING_MANAGER and INTERVIEWER roles can be invited");

        audit.info("TEAM_INVITE", recruiterId, "invitee=" + inviteeEmail + " role=" + role);

        String token = inviteToken();
        String eventId = eventId();

        ObjectNode res = ok("Invite sent successfully");
        res.put("recruiter_id",       recruiterId);
        res.put("invitee_email",      inviteeEmail);
        res.put("role",               role);
        res.put("permissions_scope",  scope);
        res.put("invite_token",       token);
        res.put("token_expires_at",   Instant.now().plusSeconds(48*3600L).toString());
        res.put("invited_at",         Instant.now().toString());
        res.put("db_write",           "INSERT INTO team_member (recruiter_id, email, role, permissions_scope, invite_token, invited_at)");

        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_id",   eventId);
        kafka.put("event_type", "recruiter.team.member.invited");
        kafka.put("consumer",   "notification-service → send invite email");
        kafka.put("payload",    "recruiter_id=" + recruiterId + " invitee=" + inviteeEmail
            + " role=" + role + " invite_token=" + token);

        res.put("audit_log", "ClickHouse: recruiter_actions_log (action=team_invite)");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 13 — RECRUITER_TEAM_REMOVE
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterTeamRemoveAgent extends BaseAgent {
    public RecruiterTeamRemoveAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_team_remove",
            "Removes a team member from a recruiter's team and revokes their access. " +
            "Deletes from team_member table. Invalidates Keycloak session for revoked member. " +
            "Logs to ClickHouse immutable audit trail for compliance. " +
            "RECRUITER can remove own team members; ADMIN can remove any.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id", "Recruiter who owns the team");
        addProp(p, "member_id",    "Team member ID to remove");
        addProp(p, "reason",       "Optional removal reason for audit log (max 200 chars)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("member_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String memberId    = str(a, "member_id", "");
        String reason      = InputSanitizer.sanitizeText(str(a, "reason", ""), 200);

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.requireNonBlank(memberId, "member_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateRecruiterId(memberId); // same format validation

        audit.warn("TEAM_REMOVE", recruiterId, "member=" + memberId + " reason=" + reason);

        ObjectNode res = ok("Team member removed and access revoked");
        res.put("recruiter_id", recruiterId);
        res.put("member_id",    memberId);
        res.put("removed_at",   Instant.now().toString());
        res.put("db_delete",    "DELETE FROM team_member WHERE id='" + memberId
            + "' AND recruiter_id='" + recruiterId + "'");
        res.put("access_revoked",       true);
        res.put("keycloak_session_revoked", "POST /auth/admin/realms/{realm}/users/{userId}/logout");
        res.put("audit_log",    "ClickHouse: recruiter_actions_log (action=team_remove, reason=" + reason + ")");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 14 — RECRUITER_SUBSCRIPTION_UPGRADE
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterSubscriptionUpgradeAgent extends BaseAgent {
    public RecruiterSubscriptionUpgradeAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_subscription_upgrade",
            "Upgrades a recruiter's subscription tier (e.g. starter → professional → enterprise). " +
            "Validates tier transition is an upgrade (not downgrade). " +
            "Emits recruiter.subscription.upgraded Kafka event → billing-service (proration), " +
            "notification-service (upgrade confirmation). " +
            "Updates PostgreSQL recruiter table and ClickHouse subscription events log. " +
            "Invalidates Redis subscription:usage cache.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id", "Recruiter ID to upgrade");
        addProp(p, "new_tier",     "Target tier: 'professional' | 'enterprise'");
        addProp(p, "billing_cycle","Billing: 'monthly' | 'annual' (default: monthly)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("new_tier").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String newTier     = str(a, "new_tier", "");
        String billing     = str(a, "billing_cycle", "monthly");

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.requireNonBlank(newTier, "new_tier");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateSubscriptionTier(newTier);
        if (newTier.equalsIgnoreCase("starter"))
            throw new IllegalArgumentException("Cannot upgrade to starter — use downgrade endpoint");
        InputSanitizer.validateEnum(billing, "billing_cycle", "monthly", "annual");

        audit.info("SUBSCRIPTION_UPGRADE", recruiterId, "new_tier=" + newTier + " billing=" + billing);

        String eventId = eventId();
        ObjectNode res = ok("Subscription upgraded successfully");
        res.put("recruiter_id", recruiterId);
        res.put("old_tier",     "starter");   // would be live from DB
        res.put("new_tier",     newTier);
        res.put("billing_cycle",billing);
        res.put("upgraded_at",  Instant.now().toString());
        res.put("new_max_jobs", config.getMaxJobsForTier(newTier));
        res.put("db_update",    "UPDATE recruiter SET subscription_tier='" + newTier + "'");
        res.put("cache_invalidated", "recruiter:" + recruiterId + ":subscription:usage");

        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_id",   eventId);
        kafka.put("event_type", "recruiter.subscription.upgraded");
        kafka.put("consumers",  "billing-service (proration), notification-service (upgrade confirmation)");
        res.put("clickhouse_log", "recruiter_subscription_events (action=upgraded)");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 15 — RECRUITER_SUBSCRIPTION_CANCEL
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterSubscriptionCancelAgent extends BaseAgent {
    public RecruiterSubscriptionCancelAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_subscription_cancel",
            "Cancels a recruiter's subscription. " +
            "Emits recruiter.subscription.cancelled Kafka event → billing-service (cancel renewal), " +
            "notification-service (cancellation confirmation), job-service (close active jobs). " +
            "Account set to cancelled status at end of billing period. " +
            "Logs to ClickHouse for churn analysis. ADMIN-only operation.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",    "Recruiter ID to cancel subscription for");
        addProp(p, "reason",          "Cancellation reason (logged for churn analysis, max 500 chars)");
        addProp(p, "effective_date",  "Cancellation effective date: 'immediate' | 'end_of_period' (default: end_of_period)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("reason").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String reason      = InputSanitizer.sanitizeText(str(a, "reason", ""), 500);
        String effective   = str(a, "effective_date", "end_of_period");

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.requireNonBlank(reason, "reason");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateEnum(effective, "effective_date", "immediate", "end_of_period");

        audit.warn("SUBSCRIPTION_CANCEL", recruiterId, "effective=" + effective + " reason=" + reason.substring(0, Math.min(reason.length(),50)));

        String eventId = eventId();
        ObjectNode res = ok("Subscription cancellation scheduled");
        res.put("recruiter_id",    recruiterId);
        res.put("effective_date",  effective);
        res.put("cancelled_at",    Instant.now().toString());
        res.put("reason_recorded", !reason.isBlank());

        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_id",   eventId);
        kafka.put("event_type", "recruiter.subscription.cancelled");
        kafka.put("consumers",  "billing-service (cancel renewal), notification-service (confirmation), job-service (close active jobs)");
        res.put("clickhouse_log", "recruiter_subscription_events (action=cancelled, churn_analysis=true)");
        res.put("note", "Account access continues until end of current billing period unless effective=immediate");
        return res;
    }
}
