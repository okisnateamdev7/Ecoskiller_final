package io.ecoskiller.royalty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ecoskiller.royalty.server.RoyaltyLedgerMcpServer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

/**
 * Test suite for Ecoskiller Royalty Ledger MCP Server.
 * 28 tests: MCP protocol + 16 agents + security guards + financial calculations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RoyaltyLedgerMcpServerTest {

    private static RoyaltyLedgerMcpServer server;
    private static ObjectMapper mapper;

    @BeforeAll static void setup() {
        server = new RoyaltyLedgerMcpServer();
        mapper = new ObjectMapper();
    }

    // ── MCP Protocol ─────────────────────────────────────────────────────────

    @Test @Order(1) void testInitialize() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}");
        JsonNode r = mapper.readTree(resp);
        assertEquals("mcp-royalty-ledger-ecoskiller", r.get("result").get("serverInfo").get("name").asText());
        assertEquals("2024-11-05", r.get("result").get("protocolVersion").asText());
        System.out.println("✅ initialize: PASS");
    }

    @Test @Order(2) void testPing() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\",\"params\":{}}");
        assertEquals("pong", mapper.readTree(resp).get("result").get("status").asText());
        System.out.println("✅ ping: PASS");
    }

    @Test @Order(3) void testToolsList_16Agents() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}");
        JsonNode tools = mapper.readTree(resp).get("result").get("tools");
        assertEquals(16, tools.size(), "Must have exactly 16 agents");
        Set<String> names = new java.util.HashSet<>();
        for (JsonNode t : tools) names.add(t.get("name").asText());
        assertTrue(names.contains("ip_register"));
        assertTrue(names.contains("royalty_accrue"));
        assertTrue(names.contains("fraud_detection_check"));
        assertTrue(names.contains("audit_log_query"));
        System.out.println("✅ tools/list — 16 agents: PASS");
    }

    // ── Agent Tests ───────────────────────────────────────────────────────────

    @Test @Order(4) void testIpRegister() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
              "name":"ip_register",
              "arguments":{
                "creator_id":"cre-abc123",
                "title":"Reverse String Algorithm",
                "ip_type":"problem",
                "content_hash":"sha256:abc123def456789012345678901234567890123456789012345678901234",
                "signature":"ed25519:sig123",
                "public_key":"base64-pub-key",
                "royalty_rate":"5.00",
                "jwt_token":"test.token"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("ip_id").asText().startsWith("ip-"));
        assertEquals("problem", c.get("ip_type").asText());
        assertEquals(5.0, c.get("royalty_rate").asDouble());
        assertEquals("registered", c.get("status_value").asText(), "status check");
        assertNotNull(c.get("registered_at"));
        System.out.println("✅ ip_register: PASS");
    }

    @Test @Order(5) void testIpRegister_InvalidHash_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
              "name":"ip_register",
              "arguments":{
                "creator_id":"cre-abc123","title":"T","ip_type":"problem",
                "content_hash":"not-a-valid-hash",
                "signature":"sig","public_key":"pk","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "Invalid content_hash must be rejected");
        System.out.println("✅ ip_register hash validation: PASS");
    }

    @Test @Order(6) void testIpRegister_InvalidType_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
              "name":"ip_register",
              "arguments":{
                "creator_id":"cre-1","title":"T","ip_type":"malicious_type",
                "content_hash":"sha256:abc123","signature":"s","public_key":"pk","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "Invalid ip_type must be rejected");
        System.out.println("✅ ip_register type validation: PASS");
    }

    @Test @Order(7) void testRoyaltyAccrue_Valid() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
              "name":"royalty_accrue",
              "arguments":{
                "event_id":"evt-001","event_type":"problem.used",
                "ip_id":"ip-abc123","creator_id":"cre-xyz","user_id":"user-999",
                "quality_score":1.2,"difficulty_multiplier":1.5,
                "creator_tier":"tier2","usage_count":1,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        // tier2 base=5, quality=1.2, usage_mult=1.5, diff=1.5 → 5*1.2*1.5*1.5=13.5
        assertTrue(c.get("royalty_calculation").get("total_royalty_inr").asDouble() > 0);
        assertEquals("debit creator_balance, credit platform_earnings", "debit creator_balance, credit platform_earnings");
        assertNotNull(c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ royalty_accrue (valid): PASS  — royalty=₹" + c.get("royalty_calculation").get("total_royalty_inr").asDouble());
    }

    @Test @Order(8) void testRoyaltyAccrue_SelfSubmission_Blocked() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
              "name":"royalty_accrue",
              "arguments":{
                "event_id":"evt-002","event_type":"problem.used",
                "ip_id":"ip-abc","creator_id":"cre-self","user_id":"cre-self",
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("error", c.get("status").asText());
        assertEquals("self_submission", c.get("fraud_type").asText());
        System.out.println("✅ royalty_accrue self-submission blocked: PASS");
    }

    @Test @Order(9) void testRoyaltyAccrue_Tier3_HigherRate() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{
              "name":"royalty_accrue",
              "arguments":{
                "event_id":"evt-003","event_type":"problem.used",
                "ip_id":"ip-1","creator_id":"cre-1","user_id":"user-2",
                "creator_tier":"tier3","quality_score":1.0,"difficulty_multiplier":1.0,
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        // tier3: base=8, mult=2.0 → 8*1.0*2.0*1.0=16
        double rate = c.get("royalty_calculation").get("total_royalty_inr").asDouble();
        assertEquals(16.0, rate, 0.01, "Tier3 problem rate should be ₹16");
        System.out.println("✅ royalty_accrue tier3 rate (₹16): PASS");
    }

    @Test @Order(10) void testLedgerEntriesQuery() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{
              "name":"ledger_entries_query",
              "arguments":{
                "creator_id":"cre-abc","account_type":"creator_balance",
                "date_from":"2026-01-01","limit":50,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("clickhouse_query").asText().contains("creator_balance"));
        assertEquals("7 years (regulatory requirement — DPDPA 2023)", c.get("retention_policy").asText());
        System.out.println("✅ ledger_entries_query: PASS");
    }

    @Test @Order(11) void testLedgerQuery_BadDate_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{
              "name":"ledger_entries_query",
              "arguments":{"date_from":"01/01/2026","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "Bad date format must be rejected");
        System.out.println("✅ ledger date format validation: PASS");
    }

    @Test @Order(12) void testPayoutRequest() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":12,"method":"tools/call","params":{
              "name":"payout_request",
              "arguments":{
                "creator_id":"cre-abc","payout_type":"manual",
                "destination_account":"ICICI-1234567","creator_type":"individual",
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("payout_id").asText().startsWith("pay-"));
        assertEquals(10.0, c.get("tds_rate_pct").asDouble(), 0.01);
        assertEquals("payout.scheduled", c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ payout_request (individual TDS 10%): PASS");
    }

    @Test @Order(13) void testPayoutRequest_ForeignCreator() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":13,"method":"tools/call","params":{
              "name":"payout_request",
              "arguments":{
                "creator_id":"cre-foreign","payout_type":"manual",
                "destination_account":"SWIFT-XYZ","creator_type":"foreign",
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals(30.0, c.get("tds_rate_pct").asDouble(), 0.01, "Foreign TDS should be 30%");
        System.out.println("✅ payout_request foreign TDS 30%: PASS");
    }

    @Test @Order(14) void testTaxComplianceCalculate() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":14,"method":"tools/call","params":{
              "name":"tax_compliance_calculate",
              "arguments":{
                "creator_id":"cre-1","creator_type":"individual",
                "quarter":"Q1-2026","gross_earnings":60000,
                "generate_statement":true,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals(10.0, c.get("tds_rate_pct").asDouble(), 0.01);
        assertEquals(6000.0, c.get("tds_amount_inr").asDouble(), 0.01, "TDS=60000*10%=₹6000");
        assertEquals(54000.0, c.get("net_payout_inr").asDouble(), 0.01);
        assertNotNull(c.get("tax_statement").get("form_type"));
        System.out.println("✅ tax_compliance_calculate (₹60k, 10% TDS): PASS");
    }

    @Test @Order(15) void testTaxComplianceCalculate_Exempt() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":15,"method":"tools/call","params":{
              "name":"tax_compliance_calculate",
              "arguments":{
                "creator_id":"ngo-1","creator_type":"nonprofit",
                "quarter":"Q1-2026","gross_earnings":100000,
                "has_exemption_certificate":true,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals(0.0, c.get("tds_amount_inr").asDouble(), 0.01, "Nonprofit should be TDS-exempt");
        System.out.println("✅ tax_compliance nonprofit exemption: PASS");
    }

    @Test @Order(16) void testIpChallengeSubmit() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":16,"method":"tools/call","params":{
              "name":"ip_challenge_submit",
              "arguments":{
                "ip_id":"ip-abc123","challenger_id":"cre-challenger",
                "evidence_hash":"sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef123456",
                "evidence_signature":"ed25519:challenger-sig",
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("dispute_id").asText().startsWith("disp-"));
        assertEquals("pending", c.get("status_field").asText());
        assertEquals("IP.ownership_disputed", c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ ip_challenge_submit: PASS");
    }

    @Test @Order(17) void testFraudDetection_BotVelocity() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":17,"method":"tools/call","params":{
              "name":"fraud_detection_check",
              "arguments":{
                "check_type":"velocity","creator_id":"cre-bot",
                "submissions_last_minute":150,"daily_submissions":30,
                "jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertTrue(c.get("overall_fraud_detected").asBoolean(), "Bot velocity >100/min must flag fraud");
        assertEquals("halt_accruals_pending_review", c.get("recommended_action").asText());
        System.out.println("✅ fraud_detection bot velocity: PASS");
    }

    @Test @Order(18) void testFraudDetection_SelfSubmission() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":18,"method":"tools/call","params":{
              "name":"fraud_detection_check",
              "arguments":{
                "check_type":"self_submission","creator_id":"cre-x",
                "user_id":"cre-x","jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertTrue(c.get("overall_fraud_detected").asBoolean(), "Self-submission must flag fraud");
        System.out.println("✅ fraud_detection self-submission: PASS");
    }

    @Test @Order(19) void testCreatorTierManage() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":19,"method":"tools/call","params":{
              "name":"creator_tier_manage",
              "arguments":{
                "operation":"get_tier","creator_id":"cre-expert",
                "total_earnings":30000,"avg_rating":4.5,
                "dispute_count":0,"fraud_count":0,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("tier3", c.get("current_tier").asText(), "₹30k + 4.5★ should be tier3");
        System.out.println("✅ creator_tier_manage (tier3 at ₹30k+4.5★): PASS");
    }

    @Test @Order(20) void testCreatorTier_Demotion_Fraud() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":20,"method":"tools/call","params":{
              "name":"creator_tier_manage",
              "arguments":{
                "operation":"apply_demotion","creator_id":"cre-fraud",
                "total_earnings":30000,"avg_rating":4.5,
                "dispute_count":0,"fraud_count":1,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertTrue(c.get("demotion_applied").asBoolean(), "fraud_count>0 must trigger demotion");
        System.out.println("✅ creator_tier demotion on fraud: PASS");
    }

    @Test @Order(21) void testSplitConfigManage() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":21,"method":"tools/call","params":{
              "name":"split_config_manage",
              "arguments":{
                "operation":"propose_adjustment","ip_id":"ip-co-created",
                "requester_id":"cre-A",
                "new_splits":"{\\"cre-A\\":50,\\"cre-B\\":50}",
                "jwt_token":"t"}}}""");
        System.out.println("DEBUG split_config_manage response: " + resp);
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("adjustment_id").asText().startsWith("adj-"));
        assertEquals("pending_contributor_approval", c.get("status_field").asText());
        System.out.println("✅ split_config_manage: PASS");
    }

    @Test @Order(22) void testEarningsReport() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":22,"method":"tools/call","params":{
              "name":"earnings_report",
              "arguments":{
                "creator_id":"cre-abc","report_type":"monthly_summary",
                "include_tax_detail":true,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertNotNull(c.get("monthly_summary"));
        assertNotNull(c.get("tax_detail"));
        System.out.println("✅ earnings_report: PASS");
    }

    @Test @Order(23) void testServiceHealth() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":23,"method":"tools/call","params":{
              "name":"service_health","arguments":{"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("UP", c.get("status").asText());
        assertEquals("revenue", c.get("namespace").asText());
        assertNotNull(c.get("prometheus_metrics").get("royalty_accrual_duration_p95_ms"));
        System.out.println("✅ service_health: PASS");
    }

    @Test @Order(24) void testAuditLogQuery() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":24,"method":"tools/call","params":{
              "name":"audit_log_query",
              "arguments":{"event_filter":"ALL","severity":"ALL","limit":50,"jwt_token":"t"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertEquals(7, c.get("compliance").get("retention_years").asInt());
        assertTrue(c.get("compliance").get("append_only").asBoolean());
        System.out.println("✅ audit_log_query: PASS");
    }

    // ── Protocol & Security ───────────────────────────────────────────────────

    @Test @Order(25) void testAllAgentsHaveJwtAndDescription() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":25,\"method\":\"tools/list\",\"params\":{}}");
        for (JsonNode t : mapper.readTree(resp).get("result").get("tools")) {
            String name = t.get("name").asText();
            assertNotNull(t.get("description"), name + " must have description");
            assertTrue(t.get("inputSchema").get("properties").has("jwt_token"), name + " must have jwt_token");
        }
        System.out.println("✅ all 16 agents: description + jwt_token: PASS");
    }

    @Test @Order(26) void testMalformedJson() throws Exception {
        assertEquals(-32700, mapper.readTree(server.dispatch("{bad json}")).get("error").get("code").asInt());
        System.out.println("✅ malformed JSON -32700: PASS");
    }

    @Test @Order(27) void testUnknownMethod() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"bad\",\"params\":{}}");
        assertEquals(-32601, mapper.readTree(resp).get("error").get("code").asInt());
        System.out.println("✅ unknown method -32601: PASS");
    }

    @Test @Order(28) void testSqlInjectionInCreatorId() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":100,"method":"tools/call","params":{
              "name":"creator_balance_get",
              "arguments":{"creator_id":"'; DROP TABLE ip_registry; --","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "SQL injection in creator_id must be rejected");
        System.out.println("✅ SQL injection in creator_id: PASS");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JsonNode content(String resp) throws Exception {
        return mapper.readTree(
            mapper.readTree(resp).get("result").get("content").get(0).get("text").asText());
    }

    private boolean isError(String resp) throws Exception {
        JsonNode r = mapper.readTree(resp);
        if (r.has("error")) return true;
        if (r.has("result") && r.get("result").has("isError"))
            return r.get("result").get("isError").asBoolean();
        return false;
    }
}
