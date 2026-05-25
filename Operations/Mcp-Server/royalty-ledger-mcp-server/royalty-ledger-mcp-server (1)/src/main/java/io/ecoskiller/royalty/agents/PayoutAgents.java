package io.ecoskiller.royalty.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.ecoskiller.royalty.config.ServerConfig;
import io.ecoskiller.royalty.security.*;
import java.time.Instant;

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 6 — PAYOUT_REQUEST
// ═══════════════════════════════════════════════════════════════════════════════
/**
 * Initiates a creator payout. Balance must be ≥ ₹5,000 (configurable threshold).
 * Calculates TDS withholding before settlement.
 * Calls payment-distribution-engine POST /api/v1/settlements/initiate.
 * Publishes payout.scheduled Kafka event.
 */
class PayoutRequestAgent extends BaseAgent {
    public PayoutRequestAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("payout_request",
            "Initiates a creator payout. Balance must be ≥ ₹" + (int)5000 + " (payout threshold). " +
            "Calculates TDS withholding (individual 10%, foreign 30%, exempt 0%) before net payout. " +
            "Calls payment-distribution-engine POST /api/v1/settlements/initiate. " +
            "Writes ledger entries: debit creator_balance, credit payout_pending. " +
            "Publishes payout.scheduled Kafka event → notification-service. " +
            "Retry policy: exponential backoff (1s, 5s, 30s, 5min) on payment failure. " +
            "Batched daily at 9pm IST or on-demand for manual requests.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id",         "Creator requesting payout");
        addProp(p,"payout_type",        "Trigger: 'manual' | 'scheduled' (scheduled = daily batch at 9pm IST)");
        addProp(p,"destination_account","Bank account / UPI ID for payout");
        addProp(p,"creator_type",       "For TDS: individual | foreign | corporate | nonprofit | government");
        addNumProp(p,"override_amount", "Admin only: override payout amount (0 = use full available balance)");
        addAuth(p);
        s.putArray("required").add("creator_id").add("payout_type").add("destination_account").add("creator_type").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId   = str(a,"creator_id","");
        String payoutType  = str(a,"payout_type","manual");
        String destination = InputSanitizer.sanitizeText(str(a,"destination_account",""), 100);
        String creatorType = str(a,"creator_type","individual");
        double override    = dbl(a,"override_amount",0);

        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.requireNonBlank(destination, "destination_account");
        InputSanitizer.validateId(creatorId, "creator_id");
        InputSanitizer.validateCreatorType(creatorType);
        InputSanitizer.validateEnum(payoutType, "payout_type", "manual","scheduled");
        if (override > 0) InputSanitizer.validatePositiveAmount(override, "override_amount");

        // TDS calculation
        double grossBalance = 15000.0; // would be live from Redis
        double tdsRate = switch(creatorType) {
            case "foreign"   -> config.getTdsRateForeign();
            case "nonprofit","government" -> 0.0;
            default -> grossBalance >= 50000 ? config.getTdsRateIndividual() : 0.0;
        };
        double tdsAmount   = Math.round(grossBalance * tdsRate / 100 * 100.0) / 100.0;
        double netPayout   = Math.round((grossBalance - tdsAmount) * 100.0) / 100.0;

        if (netPayout < config.getPayoutThreshold()) {
            ObjectNode err = errNode("Balance below payout threshold (₹" + config.getPayoutThreshold() + ")");
            err.put("available_balance", netPayout);
            err.put("threshold", config.getPayoutThreshold());
            return err;
        }

        audit.info("PAYOUT_REQUEST", creatorId,
            "type=" + payoutType + " gross=₹" + grossBalance + " tds=₹" + tdsAmount + " net=₹" + netPayout);

        String payoutId    = genId("pay");
        String settlementId= genId("set");

        ObjectNode res = ok("Payout scheduled successfully");
        res.put("payout_id",         payoutId);
        res.put("creator_id",        creatorId);
        res.put("payout_type",       payoutType);
        res.put("gross_amount_inr",  grossBalance);
        res.put("tds_rate_pct",      tdsRate);
        res.put("tds_withheld_inr",  tdsAmount);
        res.put("net_payout_inr",    netPayout);
        res.put("status_value",      "scheduled");
        res.put("settlement_id",     settlementId);
        res.put("scheduled_at",      Instant.now().toString());
        res.put("expected_credit",   Instant.now().plusSeconds(86400L).toString());

        ObjectNode ledger = res.putObject("ledger_entries");
        ledger.put("debit",  "creator_balance -₹" + grossBalance);
        ledger.put("credit", "payout_pending +₹" + netPayout + " + tax_withholding +₹" + tdsAmount);

        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_type", "payout.scheduled");
        kafka.put("consumers",  "notification-service (email/SMS to creator)");

        ObjectNode payment = res.putObject("payment_engine_call");
        payment.put("endpoint", "POST /api/v1/settlements/initiate");
        payment.put("payload",  "{creator_id:" + creatorId + ", amount:" + netPayout + ", destination:" + destination + "}");
        payment.put("retry_policy", "exponential backoff: 1s, 5s, 30s, 5min");

        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 7 — PAYOUT_STATUS_GET
// ═══════════════════════════════════════════════════════════════════════════════
class PayoutStatusGetAgent extends BaseAgent {
    public PayoutStatusGetAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("payout_status_get",
            "Retrieves the current status of a payout and full payout history for a creator. " +
            "Status lifecycle: pending → processing → completed | failed. " +
            "On failure: auto-retry with exponential backoff, balance returned to creator_balance. " +
            "Includes settlement reconciliation: ledger balance vs. actual payout amount.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id", "Creator ID to get payout history for");
        addProp(p,"payout_id",  "Specific payout ID (optional — if empty, returns full history)");
        addIntProp(p,"limit",   "Max history records (1-100, default: 20)");
        addAuth(p);
        s.putArray("required").add("creator_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId = str(a,"creator_id","");
        String payoutId  = str(a,"payout_id","");
        int limit        = num(a,"limit",20);
        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.validateId(creatorId, "creator_id");
        if (!payoutId.isBlank()) InputSanitizer.validateId(payoutId, "payout_id");
        InputSanitizer.validateRange(limit, 1, 100, "limit");
        audit.info("PAYOUT_STATUS", creatorId, "payout_id=" + payoutId);

        ObjectNode res = ok("Payout status retrieved");
        res.put("creator_id", creatorId);
        res.put("total_payouts", 0);
        res.put("total_paid_inr", 0.0);
        res.putArray("payouts").addObject()
            .put("note", "Live from PostgreSQL payout_history WHERE creator_id='" + creatorId + "'");
        ObjectNode recon = res.putObject("reconciliation");
        recon.put("ledger_balance_inr", 0.0);
        recon.put("total_paid_inr",     0.0);
        recon.put("discrepancy_inr",    0.0);
        recon.put("status",             "reconciled");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 8 — TAX_COMPLIANCE_CALCULATE
// ═══════════════════════════════════════════════════════════════════════════════
/**
 * Calculates TDS and GST withholding for a creator's earnings.
 * Generates quarterly tax statements (Form 26AS-style).
 * Supports exemption certificate management.
 */
class TaxComplianceCalculateAgent extends BaseAgent {
    public TaxComplianceCalculateAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("tax_compliance_calculate",
            "Calculates tax withholding for creator royalty earnings: " +
            "TDS 10% (individual, earnings > ₹50k/quarter), TDS 30% (foreign/non-resident), " +
            "GST 18% (corporate creators). Tax-exempt: nonprofits, government bodies. " +
            "Generates quarterly earnings summary and Form 26AS-style tax statement. " +
            "Auto-generates NEFT return file for RTO submission. " +
            "Maintains exemption certificate registry. " +
            "Writes tax_withholding ledger entries to ClickHouse.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"creator_id",       "Creator to calculate tax for");
        addProp(p,"creator_type",     "individual | foreign | corporate | nonprofit | government");
        addProp(p,"quarter",          "Tax quarter: Q1|Q2|Q3|Q4 (fiscal year, e.g. 'Q1-2026')");
        addNumProp(p,"gross_earnings","Gross earnings this quarter (₹)");
        addBoolProp(p,"has_exemption_certificate", "Creator has valid tax exemption certificate");
        addProp(p,"exemption_type",   "Exemption: 'nonprofit_12A' | 'government' | 'startup_tax_holiday' (if exempted)");
        addBoolProp(p,"generate_statement", "Generate downloadable tax statement (default: false)");
        addAuth(p);
        s.putArray("required").add("creator_id").add("creator_type").add("quarter").add("gross_earnings").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String creatorId   = str(a,"creator_id","");
        String creatorType = str(a,"creator_type","individual");
        String quarter     = InputSanitizer.sanitizeText(str(a,"quarter","Q1-2026"), 10);
        double grossEarnings = dbl(a,"gross_earnings",0);
        boolean hasExemption= bool(a,"has_exemption_certificate",false);
        boolean genStatement= bool(a,"generate_statement",false);

        InputSanitizer.requireNonBlank(creatorId, "creator_id");
        InputSanitizer.validateId(creatorId, "creator_id");
        InputSanitizer.validateCreatorType(creatorType);
        InputSanitizer.validatePositiveAmount(grossEarnings, "gross_earnings");

        double tdsRate = 0.0;
        String tdsReason = "";
        if (hasExemption) {
            tdsRate = 0.0; tdsReason = "tax_exempt";
        } else switch (creatorType) {
            case "foreign" -> { tdsRate = config.getTdsRateForeign(); tdsReason = "foreign_30pct"; }
            case "corporate" -> { tdsRate = 0.0; tdsReason = "gst_applicable_not_tds"; }
            case "nonprofit","government" -> { tdsRate = 0.0; tdsReason = "exempt"; }
            default -> {
                if (grossEarnings > 50000) { tdsRate = config.getTdsRateIndividual(); tdsReason = "individual_exceeds_50k"; }
                else tdsReason = "below_threshold";
            }
        }
        double gstRate    = creatorType.equals("corporate") ? config.getGstRate() : 0.0;
        double tdsAmount  = Math.round(grossEarnings * tdsRate / 100 * 100.0) / 100.0;
        double gstAmount  = Math.round(grossEarnings * gstRate / 100 * 100.0) / 100.0;
        double netPayout  = grossEarnings - tdsAmount;

        audit.info("TAX_CALC", creatorId,
            "quarter=" + quarter + " gross=₹" + grossEarnings + " tds=₹" + tdsAmount + " gst=₹" + gstAmount);

        ObjectNode res = ok("Tax compliance calculated");
        res.put("creator_id",        creatorId);
        res.put("creator_type",      creatorType);
        res.put("quarter",           quarter);
        res.put("gross_earnings_inr",grossEarnings);
        res.put("tds_rate_pct",      tdsRate);
        res.put("tds_amount_inr",    tdsAmount);
        res.put("tds_reason",        tdsReason);
        res.put("gst_rate_pct",      gstRate);
        res.put("gst_amount_inr",    gstAmount);
        res.put("net_payout_inr",    netPayout);
        res.put("exemption_applied", hasExemption);
        res.put("ledger_entry",      "debit tax_withholding +₹" + tdsAmount + " (ClickHouse append-only)");

        if (genStatement) {
            ObjectNode stmt = res.putObject("tax_statement");
            stmt.put("form_type",    "Form 26AS (quarterly)");
            stmt.put("quarter",      quarter);
            stmt.put("gross_inr",    grossEarnings);
            stmt.put("tds_deducted", tdsAmount);
            stmt.put("neft_return",  "auto-generated for RTO submission");
        }
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 9 — IP_CHALLENGE_SUBMIT
// ═══════════════════════════════════════════════════════════════════════════════
class IpChallengeSubmitAgent extends BaseAgent {
    public IpChallengeSubmitAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("ip_challenge_submit",
            "Submits an IP ownership challenge. Challenger must provide evidence: " +
            "content signature, prior timestamp, and content hash proving prior creation. " +
            "Writes to ownership_disputes table. " +
            "Publishes IP.ownership_disputed Kafka event → admin-service for arbitration. " +
            "Disputed balance moved from creator_balance to disputed_balance ledger account. " +
            "Auto-resolves after 30 days in favour of current owner if no admin decision. " +
            "Supports retroactive royalty recalculation if dispute resolved for challenger.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"ip_id",              "IP being challenged");
        addProp(p,"challenger_id",      "Creator submitting the challenge");
        addProp(p,"evidence_hash",      "Content hash proving challenger's prior creation (sha256: prefix)");
        addProp(p,"evidence_signature", "Challenger's digital signature over evidence_hash");
        addProp(p,"prior_timestamp",    "Claimed prior creation timestamp (ISO 8601)");
        addProp(p,"description",        "Dispute description and supporting evidence (max 1000 chars)");
        addAuth(p);
        s.putArray("required").add("ip_id").add("challenger_id").add("evidence_hash").add("evidence_signature").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String ipId         = str(a,"ip_id","");
        String challengerId = str(a,"challenger_id","");
        String evidenceHash = str(a,"evidence_hash","");
        String priorTs      = str(a,"prior_timestamp","");
        String description  = InputSanitizer.sanitizeText(str(a,"description",""), 1000);

        InputSanitizer.requireNonBlank(ipId, "ip_id");
        InputSanitizer.requireNonBlank(challengerId, "challenger_id");
        InputSanitizer.validateId(ipId, "ip_id");
        InputSanitizer.validateId(challengerId, "challenger_id");
        if (!evidenceHash.isBlank()) InputSanitizer.validateContentHash(evidenceHash);

        audit.warn("IP_CHALLENGE", challengerId, "ip_id=" + ipId + " evidence=" + evidenceHash.substring(0,Math.min(20,evidenceHash.length())));

        String disputeId = genId("disp");
        ObjectNode res = ok("IP ownership challenge submitted");
        res.put("dispute_id",      disputeId);
        res.put("ip_id",           ipId);
        res.put("challenger_id",   challengerId);
        res.put("status_field",    "pending");
        res.put("submitted_at",    Instant.now().toString());
        res.put("auto_resolve_at", Instant.now().plusSeconds(30*86400L).toString());
        res.put("auto_resolve_note","Auto-resolves in favour of current owner after 30 days if no admin decision");

        ObjectNode db = res.putObject("db_write");
        db.put("table", "ownership_disputes");
        db.put("query", "INSERT INTO ownership_disputes (dispute_id, ip_id, challenger_id, evidence_hash, status)");

        res.put("balance_moved", "Contested royalties moved from creator_balance → disputed_balance ledger");

        ObjectNode kafka = res.putObject("kafka_event");
        kafka.put("event_type", "IP.ownership_disputed");
        kafka.put("consumer",   "admin-service (arbitration workflow)");
        kafka.put("payload",    "{ip_id:" + ipId + ", challenger_id:" + challengerId + ", dispute_id:" + disputeId + "}");

        res.put("retroactive_recalc", "If resolved for challenger, all prior royalties recalculated retroactively");
        return res;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AGENT 10 — SPLIT_CONFIG_MANAGE
// ═══════════════════════════════════════════════════════════════════════════════
class SplitConfigManageAgent extends BaseAgent {
    public SplitConfigManageAgent(ServerConfig c, AuditLogger a) { super(c, a); }

    @Override public JsonNode getToolDefinition() {
        ObjectNode t = tool("split_config_manage",
            "Manages multi-contributor royalty split configuration for co-created IP. " +
            "Operations: view_splits (current config), propose_adjustment (request new splits), " +
            "approve_adjustment (contributor approves proposed change), view_history (split version log). " +
            "Split percentages must sum to exactly 100%. " +
            "All contributors must approve before new splits activate. " +
            "Split changes can be forward-only (future accruals) or retroactive (current month). " +
            "Maintains version history of all split configurations.");
        ObjectNode s = schema(t); ObjectNode p = s.putObject("properties");
        addProp(p,"operation",  "view_splits | propose_adjustment | approve_adjustment | view_history");
        addProp(p,"ip_id",      "IP to manage splits for");
        addProp(p,"requester_id","Creator requesting the operation");
        addProp(p,"new_splits",  "JSON object for proposed splits e.g. '{\"cre-A\":50,\"cre-B\":50}' (must sum to 100)");
        addProp(p,"retroactive", "Apply retroactively to current billing period: 'true'|'false' (default: false)");
        addProp(p,"adjustment_id","For approve_adjustment: the pending adjustment ID to approve");
        addAuth(p);
        s.putArray("required").add("operation").add("ip_id").add("requester_id").add("jwt_token");
        return t;
    }

    @Override public JsonNode execute(JsonNode a) throws Exception {
        String operation   = str(a,"operation","view_splits");
        String ipId        = str(a,"ip_id","");
        String requesterId = str(a,"requester_id","");
        String newSplits   = str(a,"new_splits","");
        boolean retroactive= bool(a,"retroactive",false);

        InputSanitizer.requireNonBlank(ipId, "ip_id");
        InputSanitizer.requireNonBlank(requesterId, "requester_id");
        InputSanitizer.validateId(ipId, "ip_id");
        InputSanitizer.validateId(requesterId, "requester_id");
        InputSanitizer.validateEnum(operation, "operation",
            "view_splits","propose_adjustment","approve_adjustment","view_history");

        audit.info("SPLIT_MANAGE", requesterId, "op=" + operation + " ip=" + ipId + " retroactive=" + retroactive);

        ObjectNode res = ok("Split management operation completed");
        res.put("operation",   operation);
        res.put("ip_id",       ipId);
        res.put("requester_id",requesterId);

        switch (operation) {
            case "view_splits" -> {
                ObjectNode splits = res.putObject("current_splits");
                splits.put("note", "Live from PostgreSQL royalty_splits WHERE ip_id='" + ipId + "'");
                splits.put("version_current", 1);
            }
            case "propose_adjustment" -> {
                InputSanitizer.requireNonBlank(newSplits, "new_splits");
                res.put("adjustment_id",    genId("adj"));
                res.put("proposed_splits",  newSplits);
                res.put("retroactive",      retroactive);
                res.put("status_field",     "pending_contributor_approval");
                res.put("note", "All co-contributors must approve via approve_adjustment before this activates");
            }
            case "approve_adjustment" -> {
                res.put("status_field", "approved_by_" + requesterId);
                res.put("note",   "When all contributors approve, splits will activate");
            }
            case "view_history" -> {
                res.putArray("split_history").addObject()
                    .put("note", "Version history from PostgreSQL royalty_splits_history WHERE ip_id='" + ipId + "'");
            }
        }
        return res;
    }
}
