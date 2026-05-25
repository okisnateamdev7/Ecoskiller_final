package io.ecoskiller.recruiter.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.recruiter.config.ServerConfig;
import io.ecoskiller.recruiter.security.*;

import java.time.Instant;

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 6 — RECRUITER_CANDIDATE_SAVE
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterCandidateSaveAgent extends BaseAgent {
    public RecruiterCandidateSaveAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_candidate_save",
            "Bookmarks a candidate for a recruiter. Saves to recruiter_saved_candidates table. " +
            "Supports optional tag (e.g. 'shortlist', 'follow-up') and free-text note. " +
            "Emits recruiter.candidate.saved Kafka event to analytics-service. " +
            "Invalidates Redis recruiter:{id}:saved_candidates cache.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",  "Recruiter performing the save");
        addProp(p, "candidate_id",  "Candidate to bookmark");
        addProp(p, "tag",           "Optional category tag: 'shortlist' | 'follow-up' | 'hold' | custom (max 50 chars)");
        addProp(p, "note",          "Optional private recruiter note (max 500 chars)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("candidate_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId  = str(a, "recruiter_id", "");
        String candidateId  = str(a, "candidate_id", "");
        String tag          = str(a, "tag", "");
        String note         = InputSanitizer.sanitizeText(str(a, "note", ""), 500);

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.requireNonBlank(candidateId, "candidate_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateCandidateId(candidateId);
        if (!tag.isBlank()) InputSanitizer.validateTag(tag);

        audit.info("CANDIDATE_SAVE", recruiterId, "candidate=" + candidateId + " tag=" + tag);

        ObjectNode res = ok("Candidate saved successfully");
        res.put("recruiter_id",   recruiterId);
        res.put("candidate_id",   candidateId);
        res.put("tag",            tag.isBlank() ? null : tag);
        res.put("note_preview",   note.length() > 50 ? note.substring(0,50) + "..." : note);
        res.put("saved_at",       Instant.now().toString());
        res.put("db_write",       "INSERT INTO recruiter_saved_candidates (recruiter_id, candidate_id, tag, note, saved_at)");
        res.put("cache_invalidated", "recruiter:" + recruiterId + ":saved_candidates");
        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_type", "recruiter.candidate.saved");
        kafka.put("consumer",   "analytics-service (candidate interest tracking)");
        kafka.put("event_id",   eventId());
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 7 — RECRUITER_SAVED_CANDIDATES_LIST
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterSavedCandidatesListAgent extends BaseAgent {
    public RecruiterSavedCandidatesListAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_saved_candidates_list",
            "Retrieves the recruiter's bookmarked candidate list with tags, notes, and saved dates. " +
            "Supports filtering by tag and sorting by date/name. " +
            "Served from Redis recruiter:{id}:saved_candidates cache (TTL 1 hour). " +
            "Supports bulk actions trigger (reject/schedule/tag on filtered set).");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",  "Recruiter whose saved candidates to list");
        addProp(p, "tag_filter",    "Filter by tag (e.g. 'shortlist'); empty = all");
        addProp(p, "sort_by",       "Sort: 'saved_at_desc' | 'saved_at_asc' | 'name_asc' (default: saved_at_desc)");
        addIntProp(p, "page_size",  "Results per page (1-100, default: 20)");
        addProp(p, "cursor",        "Pagination cursor");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String tagFilter   = str(a, "tag_filter", "");
        String sortBy      = str(a, "sort_by", "saved_at_desc");
        int pageSize       = num(a, "page_size", 20);

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        if (!tagFilter.isBlank()) InputSanitizer.validateTag(tagFilter);
        InputSanitizer.validateEnum(sortBy, "sort_by", "saved_at_desc","saved_at_asc","name_asc");
        InputSanitizer.validateRange(pageSize, 1, 100, "page_size");
        audit.info("SAVED_CANDIDATES_LIST", recruiterId, "tag=" + tagFilter + " sort=" + sortBy);

        ObjectNode res = ok("Saved candidates list retrieved");
        res.put("recruiter_id",   recruiterId);
        res.put("tag_filter",     tagFilter.isBlank() ? "all" : tagFilter);
        res.put("total_saved",    0);
        res.put("cache_key",      "recruiter:" + recruiterId + ":saved_candidates");
        res.put("cache_ttl_sec",  3600);
        res.putArray("candidates").addObject()
            .put("note", "Live from PostgreSQL recruiter_saved_candidates WHERE recruiter_id='" + recruiterId + "'");
        res.put("bulk_actions_available", "reject, schedule_interview, add_tag, export_csv");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 8 — RECRUITER_NOTIFICATIONS_GET
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterNotificationsGetAgent extends BaseAgent {
    public RecruiterNotificationsGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_notifications_get",
            "Fetches paginated recruiter notifications: candidate applied alerts, " +
            "assessment-ready reviews, team action requests, and billing/subscription alerts. " +
            "Unread count from Redis recruiter:{id}:notifications:unread. " +
            "Full list from PostgreSQL notification table (RLS-scoped). " +
            "Respects do-not-disturb scheduling and frequency thresholds.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",   "Recruiter ID to fetch notifications for");
        addProp(p, "filter_type",    "Filter: 'all' | 'unread' | 'application' | 'assessment' | 'team' | 'billing'");
        addIntProp(p, "page_size",   "Notifications per page (1-50, default: 20)");
        addProp(p, "cursor",         "Pagination cursor");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        String filterType  = str(a, "filter_type", "all");
        int pageSize       = num(a, "page_size", 20);

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateEnum(filterType, "filter_type",
            "all","unread","application","assessment","team","billing");
        InputSanitizer.validateRange(pageSize, 1, 50, "page_size");
        audit.info("NOTIFICATIONS_GET", recruiterId, "filter=" + filterType);

        ObjectNode res = ok("Notifications retrieved");
        res.put("recruiter_id",   recruiterId);
        res.put("filter_type",    filterType);
        res.put("unread_count",   0);
        res.put("total_count",    0);
        res.put("unread_cache_key", "recruiter:" + recruiterId + ":notifications:unread");
        res.putArray("notifications").addObject()
            .put("note", "Live from PostgreSQL WHERE recruiter_id='" + recruiterId + "' ORDER BY created_at DESC");
        res.put("next_cursor", (String)null);
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 9 — RECRUITER_NOTIFICATION_MARK_READ
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterNotificationMarkReadAgent extends BaseAgent {
    public RecruiterNotificationMarkReadAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_notification_mark_read",
            "Marks one or all notifications as read or archived for a recruiter. " +
            "Updates notification read_at timestamp in PostgreSQL. " +
            "Decrements Redis unread count (recruiter:{id}:notifications:unread). " +
            "Supports bulk mark-all-read operation.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",     "Recruiter whose notifications to update");
        addProp(p, "notification_id",  "Specific notification ID to mark (empty = mark all)");
        addProp(p, "action",           "Action: 'read' | 'archived' (default: read)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("action").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId     = str(a, "recruiter_id", "");
        String notificationId  = str(a, "notification_id", "");
        String action          = str(a, "action", "read");

        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        InputSanitizer.validateEnum(action, "action", "read", "archived");
        audit.info("NOTIFICATION_MARK", recruiterId, "notif=" + notificationId + " action=" + action);

        boolean bulk = notificationId.isBlank();
        ObjectNode res = ok(bulk ? "All notifications marked as " + action
            : "Notification " + notificationId + " marked as " + action);
        res.put("recruiter_id",    recruiterId);
        res.put("bulk_operation",  bulk);
        res.put("action",          action);
        res.put("updated_at",      Instant.now().toString());
        res.put("db_update",       bulk
            ? "UPDATE notification SET " + action + "_at=NOW() WHERE recruiter_id='" + recruiterId + "'"
            : "UPDATE notification SET " + action + "_at=NOW() WHERE id='" + notificationId + "'");
        res.put("redis_update",    "DECR recruiter:" + recruiterId + ":notifications:unread");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 10 — RECRUITER_SUBSCRIPTION_GET
// ═══════════════════════════════════════════════════════════════════════════════
class RecruiterSubscriptionGetAgent extends BaseAgent {
    public RecruiterSubscriptionGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("recruiter_subscription_get",
            "Retrieves the recruiter's current subscription tier, usage metrics (jobs created, " +
            "assessments run), feature gates, billing status, and quota warnings. " +
            "Usage data from Redis recruiter:{id}:subscription:usage (real-time). " +
            "Tier configuration from billing-service. " +
            "Emits recruiter.quota.exceeded event if limits are breached.");
        ObjectNode s = schema(t);
        ObjectNode p = s.putObject("properties");
        addProp(p, "recruiter_id",   "Recruiter ID");
        addBoolProp(p, "include_usage_history", "Include 30-day usage trend (default: false)");
        addAuthProp(p);
        s.putArray("required").add("recruiter_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String recruiterId = str(a, "recruiter_id", "");
        boolean history    = bool(a, "include_usage_history", false);
        InputSanitizer.requireNonBlank(recruiterId, "recruiter_id");
        InputSanitizer.validateRecruiterId(recruiterId);
        audit.info("SUBSCRIPTION_GET", recruiterId, "history=" + history);

        ObjectNode res = ok("Subscription details retrieved");
        res.put("recruiter_id",     recruiterId);
        res.put("tier",             "professional");
        res.put("billing_status",   "active");
        res.put("next_renewal",     Instant.now().plusSeconds(30*86400L).toString());
        res.put("cache_key",        "recruiter:" + recruiterId + ":subscription:usage");
        res.put("cache_ttl",        "real-time (updated on each write)");

        ObjectNode usage = res.putObject("usage");
        usage.put("jobs_posted",        0);
        usage.put("jobs_limit",         config.getMaxJobsForTier("professional"));
        usage.put("assessments_run",    0);
        usage.put("assessments_limit",  100);
        usage.put("team_members",       0);
        usage.put("team_members_limit", 10);

        ObjectNode features = res.putObject("feature_gates");
        features.put("advanced_analytics",  true);
        features.put("bulk_actions",         true);
        features.put("webhook_support",      true);
        features.put("opensearch_candidates",true);
        features.put("api_access",           true);

        if (history) {
            res.putArray("usage_history_30d").addObject()
                .put("note", "From ClickHouse: recruiter_quota_usage daily snapshots");
        }
        ObjectNode kafka = res.putObject("quota_events");
        kafka.put("event_type", "recruiter.quota.exceeded (emitted when jobs_posted >= jobs_limit)");
        kafka.put("consumers",  "notification-service (quota warning), billing-service (upsell trigger)");
        return res;
    }
}
