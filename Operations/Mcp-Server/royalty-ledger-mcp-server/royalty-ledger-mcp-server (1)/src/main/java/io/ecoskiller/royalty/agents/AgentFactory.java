package io.ecoskiller.royalty.agents;

import io.ecoskiller.royalty.config.ServerConfig;
import io.ecoskiller.royalty.security.AuditLogger;
import java.util.LinkedHashMap;
import java.util.Map;

public class AgentFactory {
    public static Map<String, AgentHandler> createAllAgents(ServerConfig config, AuditLogger audit) {
        Map<String, AgentHandler> agents = new LinkedHashMap<>();

        // Core IP & Royalty
        agents.put("ip_register",               new IpRegisterAgent(config, audit));
        agents.put("ip_details_get",            new IpDetailsGetAgent(config, audit));
        agents.put("royalty_accrue",            new RoyaltyAccrueAgent(config, audit));
        agents.put("ledger_entries_query",      new LedgerEntriesQueryAgent(config, audit));
        agents.put("creator_balance_get",       new CreatorBalanceGetAgent(config, audit));
        // Payout & Tax
        agents.put("payout_request",            new PayoutRequestAgent(config, audit));
        agents.put("payout_status_get",         new PayoutStatusGetAgent(config, audit));
        agents.put("tax_compliance_calculate",  new TaxComplianceCalculateAgent(config, audit));
        // Dispute & Split
        agents.put("ip_challenge_submit",       new IpChallengeSubmitAgent(config, audit));
        agents.put("split_config_manage",       new SplitConfigManageAgent(config, audit));
        // Fraud & Tier
        agents.put("fraud_detection_check",     new FraudDetectionCheckAgent(config, audit));
        agents.put("creator_tier_manage",       new CreatorTierManageAgent(config, audit));
        // Rate & Reporting
        agents.put("royalty_rate_manage",       new RoyaltyRateManageAgent(config, audit));
        agents.put("earnings_report",           new EarningsReportAgent(config, audit));
        // Health & Audit
        agents.put("service_health",            new ServiceHealthAgent(config, audit));
        agents.put("audit_log_query",           new AuditLogQueryAgent(config, audit));

        return agents;
    }
}
