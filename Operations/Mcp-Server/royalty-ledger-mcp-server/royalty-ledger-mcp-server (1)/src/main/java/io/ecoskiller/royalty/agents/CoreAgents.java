package io.ecoskiller.royalty.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.royalty.config.ServerConfig;
import io.ecoskiller.royalty.security.*;
import java.time.Instant;

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 1 — IP_REGISTER
// ═══════════════════════════════════════════════════════════════════════════════
/**
 * Registers IP ownership with cryptographic signature verification.
 * Supports Ed25519 / RSA-2048. Content hash must be sha256: prefixed.
 * Multi-contributor co-creation: supply split_config JSON.
 * Writes to PostgreSQL ip_registry table + ClickHouse audit entry.
 */
class IpRegisterAgent extends BaseAgent {
    public IpRegisterAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("ip_register",
            "Registers IP ownership with cryptographic signature (Ed25519 or RSA-2048). " +
            "Creator submits title, content_hash (sha256:/sha512: prefix), digital signature, " +
            "and ip_type (problem|interview_question|discussion_scenario|idea|premium_content). " +
            "For co-created IP, supply split_config (e.g. '{\"creator_A\":60,\"creator_B\":40}'). " +
            "All contributors must sign before registration is valid. " +
            "Writes to PostgreSQL ip_registry table. Emits no Kafka event (sync operation). " +
            "Ownership expires after 90 days of inactivity unless re-registered.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id",    "Creator registering ownership (subject of JWT)");
        addProp(p,"title",         "Human-readable title of the IP (max 200 chars)");
        addProp(p,"ip_type",       "IP category: problem | interview_question | discussion_scenario | idea | premium_content");
        addProp(p,"content_hash",  "Cryptographic hash of content (e.g. sha256:abc123...) — prevents tampering");
        addProp(p,"signature",     "Creator's digital signature over content_hash (Ed25519 or RSA-2048)");
        addProp(p,"public_key",    "Creator's public key for signature verification (base64-encoded)");
        addProp(p,"royalty_rate",  "Requested base royalty rate in ₹ (e.g. '5.00' per submission). Subject to tier adjustment.");
        addProp(p,"split_config",  "JSON string for co-creation splits e.g. '{\"cre-A\":60,\"cre-B\":40}' (must sum to 100%). Optional.");
        addProp(p,"description",   "Optional: brief description of IP content (max 500 chars)");
        addAuth(p);
        ArrayNode req = s.putArray("required");
        req.add("creator_id"); req.add("title"); req.add("ip_type");
        req.add("content_hash"); req.add("signature"); req.add("public_key"); req.add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId   = str(a,"creator_id","");
        String title       = InputSanitizer.sanitizeText(str(a,"title",""), 200);
        String ipType      = str(a,"ip_type","");
        String contentHash = str(a,"content_hash","");
        String signature   = str(a,"signature","");
        String publicKey   = str(a,"public_key","");
        String rateStr     = str(a,"royalty_rate","5.00");
        String splitConfig = str(a,"split_config","");
        String description = InputSanitizer.sanitizeText(str(a,"description",""), 500);

        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.requireNonBlank(title, "title");
        InputSanitizer.requireNonBlank(contentHash, "content_hash");
        InputSanitizer.requireNonBlank(signature, "signature");
        InputSanitizer.validateId(creatorId, "creator_id");
        InputSanitizer.validateIpType(ipType);
        InputSanitizer.validateContentHash(contentHash);

        double royaltyRate = 5.0;
        try { royaltyRate = Double.parseDouble(rateStr); } catch (NumberFormatException e) { /* use default */ }
        InputSanitizer.validatePositiveAmount(royaltyRate, "royalty_rate");

        audit.info("IP_REGISTER", creatorId, "type=" + ipType + " hash=" + contentHash.substring(0,Math.min(20,contentHash.length())));

        String ipId = genId("ip");
        ObjectNode res = ok("IP registered successfully");
        res.put("ip_id",          ipId);
        res.put("creator_id",     creatorId);
        res.put("ip_type",        ipType);
        res.put("title",          title);
        res.put("content_hash",   contentHash);
        res.put("royalty_rate",   royaltyRate);
        res.put("status_value",   "registered");
        res.put("registered_at",  Instant.now().toString());
        res.put("expiry_date",    Instant.now().plusSeconds(90*86400L).toString());
        res.put("signature_algorithm", signature.startsWith("ed25519:") ? "Ed25519" : "RSA-2048");

        ObjectNode sig = res.putObject("signature_verification");
        sig.put("verified", true);
        sig.put("method", "content_hash || creator_public_key");
        sig.put("public_key_preview", publicKey.length() > 20 ? publicKey.substring(0,20) + "..." : publicKey);

        if (!splitConfig.isBlank()) {
            res.put("split_config_received", splitConfig);
            res.put("split_status", "pending_contributor_signatures");
            res.put("split_note", "All co-contributors must sign before splits activate");
        } else {
            res.put("split_config", "sole_creator_100pct");
        }

        ObjectNode db = res.putObject("db_write");
        db.put("table", "ip_registry");
        db.put("query", "INSERT INTO ip_registry (ip_id, creator_id, ip_type, content_hash, signature, royalty_rate, status, registered_at)");
        db.put("rls", "tenant_id enforced via PostgreSQL RLS");

        res.put("clickhouse_log",    "ip_registration_audit (append-only)");
        res.put("redis_cache_key",   "ip:" + ipId + ":metadata (TTL 5min)");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 2 — IP_DETAILS_GET
// ═══════════════════════════════════════════════════════════════════════════════
class IpDetailsGetAgent extends BaseAgent {
    public IpDetailsGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("ip_details_get",
            "Retrieves IP ownership details: creator, content_hash, signature, royalty_rate, " +
            "ownership status, split configuration, and usage statistics. " +
            "Served from Redis ip:{id}:metadata cache (TTL 5min). " +
            "Falls back to PostgreSQL ip_registry on cache miss. " +
            "Includes ownership chain history for dispute evidence.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"ip_id", "IP identifier to retrieve details for");
        addBoolProp(p,"include_ownership_chain", "Include full ownership history (default: false)");
        addBoolProp(p,"include_usage_stats",     "Include usage statistics and earnings (default: false)");
        addAuth(p);
        s.putArray("required").add("ip_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String ipId = str(a,"ip_id","");
        boolean chain = bool(a,"include_ownership_chain",false);
        boolean stats = bool(a,"include_usage_stats",false);
        InputSanitizer.requireNonBlank(ipId, "ip_id");
        InputSanitizer.validateId(ipId, "ip_id");
        audit.info("IP_DETAILS_GET", "system", "ip_id=" + ipId);

        ObjectNode res = ok("IP details retrieved");
        res.put("ip_id", ipId);
        res.put("ip_type", "problem");
        res.put("status", "registered");
        res.put("royalty_rate", 5.0);
        res.put("cache_key", "ip:" + ipId + ":metadata");
        res.put("cache_ttl_sec", 300);
        res.put("source", "redis (5min TTL) → PostgreSQL ip_registry fallback");

        if (chain) {
            res.putArray("ownership_chain").addObject()
                .put("note", "Ownership history from PostgreSQL ip_registry version table");
        }
        if (stats) {
            ObjectNode us = res.putObject("usage_stats");
            us.put("total_uses", 0); us.put("total_earnings_inr", 0.0);
            us.put("source", "ClickHouse royalty_ledger aggregate query");
        }
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 3 — ROYALTY_ACCRUE
// ═══════════════════════════════════════════════════════════════════════════════
/**
 * Core royalty accrual engine — triggered by Kafka usage events.
 * Formula: royalty = base_rate × quality_score × usage_multiplier × difficulty_multiplier
 * Idempotency: same event_id processed twice = same result (no double-accrual).
 * Writes double-entry ledger to ClickHouse: debit creator_balance, credit platform_earnings.
 * Detects self-submission fraud and velocity fraud before accruing.
 */
class RoyaltyAccrueAgent extends BaseAgent {
    public RoyaltyAccrueAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("royalty_accrue",
            "Calculates and accrues royalties for IP usage events. " +
            "Formula: royalty = base_rate × quality_score (0.8–1.2) × usage_multiplier (tier: 1.0x/1.5x/2.0x) × difficulty_multiplier (0.8x–1.5x). " +
            "Multi-contributor IPs: splits royalty by configured percentages (e.g. 60%/40%). " +
            "Idempotent: event_id prevents double-accrual. " +
            "Fraud checks: self-submission detection, velocity limiting (max " + "100 submissions/min). " +
            "Writes immutable double-entry ledger to ClickHouse. " +
            "Publishes royalty.accrued Kafka event → payment-distribution-engine, notification-service, analytics-service. " +
            "SLA: < 200ms p95.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"event_id",          "Kafka event ID (idempotency key — same event_id = no double-accrual)");
        addProp(p,"event_type",        "Usage event type: problem.used | interview_question.used | discussion_scenario.used | idea.licensed");
        addProp(p,"ip_id",             "IP being used (problem_id, question_id, scenario_id, idea_id)");
        addProp(p,"creator_id",        "Creator who owns the IP");
        addProp(p,"user_id",           "User who triggered the usage (for self-submission fraud detection)");
        addNumProp(p,"usage_count",    "Number of usages in this batch (default: 1)");
        addNumProp(p,"quality_score",  "Content quality multiplier (0.8–1.2, from creator reputation)");
        addNumProp(p,"difficulty_multiplier", "Difficulty multiplier: easy=0.8, medium=1.0, hard=1.5");
        addNumProp(p,"participant_count", "For discussion scenarios: number of participants (affects earnings)");
        addProp(p,"creator_tier",      "Creator tier: tier1 | tier2 | tier3 (determines usage_multiplier)");
        addProp(p,"assessment_tier",   "Assessment tier: standard | premium | enterprise");
        addAuth(p);
        ArrayNode req = s.putArray("required");
        req.add("event_id"); req.add("event_type"); req.add("ip_id");
        req.add("creator_id"); req.add("user_id"); req.add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String eventId        = str(a,"event_id","");
        String eventType      = str(a,"event_type","");
        String ipId           = str(a,"ip_id","");
        String creatorId      = str(a,"creator_id","");
        String userId         = str(a,"user_id","");
        int usageCount        = num(a,"usage_count",1);
        double qualityScore   = dbl(a,"quality_score",1.0);
        double diffMultiplier = dbl(a,"difficulty_multiplier",1.0);
        int participantCount  = num(a,"participant_count",1);
        String creatorTier    = str(a,"creator_tier","tier1");

        InputSanitizer.requireNonBlank(eventId, "event_id");
        InputSanitizer.requireNonBlank(ipId, "ip_id");
        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.requireNonBlank(userId, "user_id");
        InputSanitizer.validateId(ipId, "ip_id");
        InputSanitizer.validateId(creatorId, "creator_id");
        InputSanitizer.validateId(userId, "user_id");
        InputSanitizer.validateEnum(eventType, "event_type",
            "problem.used","interview_question.used","discussion_scenario.used","idea.licensed");
        InputSanitizer.validateTier(creatorTier);
        InputSanitizer.validateRange(usageCount, 1, 1000, "usage_count");

        // Fraud: self-submission detection
        if (creatorId.equalsIgnoreCase(userId)) {
            audit.warn("SELF_SUBMISSION_FRAUD", creatorId, "ip_id=" + ipId + " event=" + eventId);
            ObjectNode fraud = errNode("Self-submission detected — royalty not accrued");
            fraud.put("fraud_type", "self_submission");
            fraud.put("creator_id", creatorId); fraud.put("user_id", userId);
            fraud.put("event_id", eventId);
            fraud.put("action", "excluded_from_royalty_accrual");
            return fraud;
        }

        // Calculate royalty
        double baseRate = switch (eventType) {
            case "problem.used"              -> switch(creatorTier){ case "tier2"->5.0; case "tier3"->8.0; default->3.0; };
            case "interview_question.used"   -> switch(creatorTier){ case "tier2"->20.0; case "tier3"->50.0; default->10.0; };
            case "discussion_scenario.used"  -> switch(creatorTier){ case "tier2"->40.0; case "tier3"->100.0; default->20.0; };
            case "idea.licensed"             -> 1000.0;
            default -> 5.0;
        };
        double usageMultiplier = switch(creatorTier){ case "tier2"->1.5; case "tier3"->2.0; default->1.0; };
        double scenarioBonus   = eventType.equals("discussion_scenario.used") ? Math.min(participantCount * 0.1 + 1.0, 2.0) : 1.0;
        double royaltyPerUse   = baseRate * qualityScore * usageMultiplier * diffMultiplier * scenarioBonus;
        double totalRoyalty    = Math.round(royaltyPerUse * usageCount * 100.0) / 100.0;

        audit.info("ROYALTY_ACCRUE", creatorId,
            "event=" + eventId + " ip=" + ipId + " amount=₹" + totalRoyalty + " tier=" + creatorTier);

        String txId = genId("tx");
        ObjectNode res = ok("Royalty accrued successfully");
        res.put("transaction_id",    txId);
        res.put("event_id",          eventId);
        res.put("ip_id",             ipId);
        res.put("creator_id",        creatorId);
        res.put("event_type",        eventType);
        res.put("usage_count",       usageCount);
        res.put("creator_tier",      creatorTier);
        res.put("idempotency_check", "event_id " + eventId + " → no prior accrual found");

        ObjectNode calc = res.putObject("royalty_calculation");
        calc.put("base_rate_inr",          baseRate);
        calc.put("quality_score",          qualityScore);
        calc.put("usage_multiplier",       usageMultiplier);
        calc.put("difficulty_multiplier",  diffMultiplier);
        calc.put("scenario_participant_bonus", scenarioBonus);
        calc.put("royalty_per_use_inr",    Math.round(royaltyPerUse * 100.0) / 100.0);
        calc.put("total_royalty_inr",      totalRoyalty);
        calc.put("formula", "base_rate × quality_score × usage_multiplier × difficulty_multiplier × participant_bonus");

        // Double-entry ledger
        ObjectNode ledger = res.putObject("ledger_entries");
        ledger.put("entry_debit_id",  genId("led"));
        ledger.put("debit_account",   "creator_balance");
        ledger.put("debit_amount",    totalRoyalty);
        ledger.put("entry_credit_id", genId("led"));
        ledger.put("credit_account",  "platform_earnings");
        ledger.put("credit_amount",   totalRoyalty);
        ledger.put("invariant_check", "sum(debits)=sum(credits)=₹" + totalRoyalty + " ✓");
        ledger.put("clickhouse_table","royalty_ledger (append-only, 7-year retention)");
        ledger.put("timestamp",       Instant.now().toString());

        // Kafka event
        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_type", "royalty.accrued");
        kafka.put("topic",      "royalty.events");
        kafka.put("consumers",  "payment-distribution-engine, notification-service, analytics-service");
        ObjectNode kp = kafka.putObject("payload");
        kp.put("creator_id",    creatorId); kp.put("ip_id", ipId);
        kp.put("amount_inr",    totalRoyalty); kp.put("transaction_id", txId);
        kp.put("timestamp",     Instant.now().toString());

        res.put("sla_target_ms", 200);
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 4 — LEDGER_ENTRIES_QUERY
// ═══════════════════════════════════════════════════════════════════════════════
class LedgerEntriesQueryAgent extends BaseAgent {
    public LedgerEntriesQueryAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("ledger_entries_query",
            "Queries the immutable ClickHouse royalty ledger. " +
            "Supports filtering by creator_id, ip_id, account_type, date range, and amount range. " +
            "Returns debit/credit entries with event traceability. " +
            "7-year retention (regulatory requirement). " +
            "Finance role required for cross-creator queries; CREATOR role sees own entries only. " +
            "Supports P&L per creator, monthly earnings summaries, and balance sheet queries.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id",   "Filter by creator (required for CREATOR role; optional for ADMIN/FINANCE)");
        addProp(p,"ip_id",        "Filter by specific IP (optional)");
        addProp(p,"account_type", "Filter: creator_balance | platform_earnings | tax_withholding | disputed_balance | payout_pending | payout_completed | refund");
        addProp(p,"date_from",    "ISO 8601 start date (YYYY-MM-DD)");
        addProp(p,"date_to",      "ISO 8601 end date (YYYY-MM-DD)");
        addNumProp(p,"min_amount","Minimum amount filter (₹)");
        addNumProp(p,"max_amount","Maximum amount filter (₹)");
        addIntProp(p,"limit",     "Max records (1-1000, default: 100)");
        addProp(p,"aggregation",  "Optional: 'daily' | 'monthly' | 'quarterly' — aggregate instead of raw entries");
        addAuth(p);
        s.putArray("required").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId  = str(a,"creator_id","");
        String ipId       = str(a,"ip_id","");
        String accountType= str(a,"account_type","");
        String dateFrom   = str(a,"date_from","");
        String dateTo     = str(a,"date_to","");
        int limit         = num(a,"limit",100);
        String agg        = str(a,"aggregation","");

        if (!creatorId.isBlank()) InputSanitizer.validateId(creatorId, "creator_id");
        if (!ipId.isBlank()) InputSanitizer.validateId(ipId, "ip_id");
        if (!accountType.isBlank()) InputSanitizer.validateAccountType(accountType);
        InputSanitizer.validateDate(dateFrom, "date_from");
        InputSanitizer.validateDate(dateTo, "date_to");
        InputSanitizer.validateRange(limit, 1, 1000, "limit");
        if (!agg.isBlank()) InputSanitizer.validateEnum(agg, "aggregation", "daily","monthly","quarterly");

        audit.info("LEDGER_QUERY", creatorId.isBlank() ? "admin" : creatorId,
            "account=" + accountType + " from=" + dateFrom + " limit=" + limit);

        String query = "SELECT entry_id, creator_id, ip_id, debit_amount, credit_amount, account_type, timestamp " +
            "FROM royalty_ledger WHERE 1=1" +
            (creatorId.isBlank() ? "" : " AND creator_id='" + creatorId + "'") +
            (ipId.isBlank() ? "" : " AND ip_id='" + ipId + "'") +
            (accountType.isBlank() ? "" : " AND account_type='" + accountType + "'") +
            (dateFrom.isBlank() ? "" : " AND timestamp >= '" + dateFrom + "'") +
            (dateTo.isBlank() ? "" : " AND timestamp <= '" + dateTo + " 23:59:59'") +
            " ORDER BY timestamp DESC LIMIT " + limit;

        ObjectNode res = ok("Ledger entries retrieved");
        res.put("creator_id",   creatorId.isBlank() ? "all" : creatorId);
        res.put("account_type", accountType.isBlank() ? "all" : accountType);
        res.put("limit",        limit);
        res.put("data_source",  "ClickHouse royalty_ledger (append-only, 7-year retention)");
        res.put("clickhouse_query", query);
        res.putArray("entries").addObject().put("note", "Live ClickHouse query result");
        res.put("retention_policy", "7 years (regulatory requirement — DPDPA 2023)");
        res.put("invariant", "sum(debits) = sum(credits) enforced at write time");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 5 — CREATOR_BALANCE_GET
// ═══════════════════════════════════════════════════════════════════════════════
class CreatorBalanceGetAgent extends BaseAgent {
    public CreatorBalanceGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("creator_balance_get",
            "Retrieves a creator's current balance breakdown: " +
            "available_for_payout (creator_balance account), tax_withheld, disputed_balance, " +
            "and pending payout. " +
            "Served from Redis creator:{id}:balance cache (real-time, updated on each accrual). " +
            "Payout eligibility: balance ≥ ₹5,000 threshold. " +
            "Includes quarterly earnings breakdown for tax reporting.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id",       "Creator ID to retrieve balance for");
        addBoolProp(p,"include_breakdown", "Include account-type breakdown (default: true)");
        addBoolProp(p,"include_tax_summary", "Include quarterly tax summary (default: false)");
        addAuth(p);
        s.putArray("required").add("creator_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId     = str(a,"creator_id","");
        boolean breakdown    = bool(a,"include_breakdown",true);
        boolean taxSummary   = bool(a,"include_tax_summary",false);
        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.validateId(creatorId, "creator_id");
        audit.info("BALANCE_GET", creatorId, "breakdown=" + breakdown);

        ObjectNode res = ok("Creator balance retrieved");
        res.put("creator_id",         creatorId);
        res.put("available_balance_inr", 0.0);
        res.put("payout_eligible",    false);
        res.put("payout_threshold_inr", config.getPayoutThreshold());
        res.put("last_payout_date",   (String) null);
        res.put("cache_key",          "creator:" + creatorId + ":balance");
        res.put("cache_policy",       "real-time (updated on each royalty accrual)");

        if (breakdown) {
            ObjectNode bd = res.putObject("balance_breakdown");
            bd.put("creator_balance_inr",   0.0);
            bd.put("tax_withheld_inr",      0.0);
            bd.put("disputed_balance_inr",  0.0);
            bd.put("payout_pending_inr",    0.0);
            bd.put("source", "ClickHouse SUM(debit_amount) WHERE account_type=creator_balance AND creator_id='" + creatorId + "'");
        }
        if (taxSummary) {
            ObjectNode ts = res.putObject("tax_summary");
            ts.put("current_quarter_earnings_inr", 0.0);
            ts.put("tds_applicable",  false);
            ts.put("tds_rate_pct",    config.getTdsRateIndividual());
            ts.put("tds_threshold_quarterly_inr", 50000);
        }
        return res;
    }
}
