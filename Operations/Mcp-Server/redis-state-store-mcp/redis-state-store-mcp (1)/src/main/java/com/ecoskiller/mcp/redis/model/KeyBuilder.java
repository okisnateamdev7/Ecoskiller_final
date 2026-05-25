package com.ecoskiller.mcp.redis.model;

/**
 * Central registry of ALL Redis key patterns for the Ecoskiller platform.
 *
 * Every key is namespaced with tenant:{tenant_id}: to prevent cross-tenant
 * key collisions within a single Redis instance (Feature F2 from the spec).
 *
 * Source: Redis State Store Service Technical Reference v1.0, Appendix A.
 */
public final class KeyBuilder {

    private KeyBuilder() {}

    // ─── GD Orchestrator ─────────────────────────────────────────────────────────

    /** Current GD session state: WAITING|INTRO|SPEAKING:{cid}|OPEN_DISCUSSION|CLOSING|COMPLETED */
    public static String gdState(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":state";
    }

    /** Phase countdown timer — SETEX; expiry triggers keyspace notification → state advance */
    public static String gdTimer(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":timer";
    }

    /** Current speaker candidate ID during SPEAKING phase (TTL=90s) */
    public static String gdCurrentSpeaker(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":current_speaker";
    }

    /** Raise-hand queue (LIST) — LPUSH on raise, RPOP on grant */
    public static String gdQueue(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":queue";
    }

    /** SET of joined participant IDs */
    public static String gdParticipants(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":participants";
    }

    /** Serialized session result JSON — idempotency key (TTL=24h) */
    public static String gdResult(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":result";
    }

    /** Event idempotency key — SET NX; prevents double Kafka publish on retry (TTL=24h) */
    public static String gdIdempotency(String tenantId, String sessionId) {
        return "tenant:" + tenantId + ":gd:" + sessionId + ":idempotency";
    }

    // ─── Interview Service ───────────────────────────────────────────────────────

    /** Question or session timer — PTTL read gives remaining ms */
    public static String interviewTimer(String tenantId, String interviewId) {
        return "tenant:" + tenantId + ":interview:" + interviewId + ":timer";
    }

    /** Current question index — INCR to advance */
    public static String interviewQuestion(String tenantId, String interviewId) {
        return "tenant:" + tenantId + ":interview:" + interviewId + ":question";
    }

    // ─── Auth Service — OTP ──────────────────────────────────────────────────────

    /** OTP value (TTL=900s) — DEL on successful verification (never persisted to disk) */
    public static String otp(String tenantId, String userId, String otpType) {
        return "tenant:" + tenantId + ":otp:" + userId + ":" + otpType;
    }

    // ─── Rate Limiting ───────────────────────────────────────────────────────────

    /** Sliding-window rate counter — INCR + EXPIRE per date window */
    public static String rateLimitCounter(String tenantId, String userId, String endpoint, String dateWindow) {
        return "tenant:" + tenantId + ":ratelimit:" + userId + ":" + endpoint + ":" + dateWindow;
    }

    // ─── Distributed Locks ───────────────────────────────────────────────────────

    /** Slot booking lock — SET NX PX 10000 (TTL=10s) */
    public static String slotLock(String tenantId, String slotId) {
        return "tenant:" + tenantId + ":lock:slot:" + slotId;
    }

    /** Billing cycle lock — SET NX PX 60000 (TTL=60s) */
    public static String billingLock(String tenantId, String cycleId) {
        return "tenant:" + tenantId + ":lock:billing:" + cycleId;
    }

    // ─── Leaderboards ────────────────────────────────────────────────────────────

    /** Live candidate leaderboard SORTED SET — ZADD on score, ZRANGE for read */
    public static String rankings(String tenantId, String jobId) {
        return "tenant:" + tenantId + ":rankings:" + jobId;
    }

    // ─── Dojo Match Engine ───────────────────────────────────────────────────────

    /** Dojo match state (problem index, timer, status) */
    public static String dojoMatchState(String tenantId, String matchId) {
        return "tenant:" + tenantId + ":match:" + matchId + ":state";
    }

    /** Submission attempt counter — INCR */
    public static String dojoMatchAttempts(String tenantId, String matchId) {
        return "tenant:" + tenantId + ":match:" + matchId + ":attempts";
    }

    // ─── Config Cache ────────────────────────────────────────────────────────────

    /** Per-tenant per-service config cache (TTL=300–3600s) */
    public static String tenantConfig(String tenantId, String serviceName) {
        return "tenant:" + tenantId + ":" + serviceName + ":config";
    }

    // ─── Pub/Sub Channels ────────────────────────────────────────────────────────

    /** Per-session Pub/Sub channel for WebSocket broadcast */
    public static String sessionEventsChannel(String sessionId) {
        return "session:" + sessionId + ":events";
    }

    /** Tenant-wide admin broadcast channel */
    public static String tenantBroadcastChannel(String tenantId) {
        return "tenant:" + tenantId + ":broadcast";
    }

    // ─── Billing Metering ────────────────────────────────────────────────────────

    /** Usage metering counter for billing */
    public static String billingMeter(String tenantId, String metricName) {
        return "tenant:" + tenantId + ":billing:meter:" + metricName;
    }
}
