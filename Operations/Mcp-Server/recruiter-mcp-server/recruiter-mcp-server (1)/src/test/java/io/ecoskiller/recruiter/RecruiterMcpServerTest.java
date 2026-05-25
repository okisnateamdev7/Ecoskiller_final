package io.ecoskiller.recruiter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ecoskiller.recruiter.server.RecruiterMcpServer;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Ecoskiller Recruiter MCP Server.
 * 22 tests covering: MCP protocol, all 18 agents, security guards, error handling.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecruiterMcpServerTest {

    private static RecruiterMcpServer server;
    private static ObjectMapper mapper;

    @BeforeAll static void setup() {
        System.setProperty("RECRUITER_JWT_SECRET", "test-secret-key-for-unit-tests-min32chars");
        server = new RecruiterMcpServer();
        mapper = new ObjectMapper();
    }

    // ── MCP Protocol ─────────────────────────────────────────────────────────

    @Test @Order(1)
    void testInitialize() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""");
        JsonNode r = mapper.readTree(resp);
        assertEquals("2.0", r.get("jsonrpc").asText());
        assertEquals("mcp-recruiter-ecoskiller", r.get("result").get("serverInfo").get("name").asText());
        assertEquals("2024-11-05", r.get("result").get("protocolVersion").asText());
        assertNotNull(r.get("result").get("capabilities").get("tools"));
        System.out.println("✅ initialize: PASS");
    }

    @Test @Order(2)
    void testPing() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\",\"params\":{}}");
        assertEquals("pong", mapper.readTree(resp).get("result").get("status").asText());
        System.out.println("✅ ping: PASS");
    }

    @Test @Order(3)
    void testToolsList_18Agents() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/list\",\"params\":{}}");
        JsonNode tools = mapper.readTree(resp).get("result").get("tools");
        assertEquals(18, tools.size(), "Must have exactly 18 agents");
        Set<String> names = new java.util.HashSet<>();
        for (JsonNode t : tools) names.add(t.get("name").asText());
        assertTrue(names.contains("recruiter_account_onboard"));
        assertTrue(names.contains("recruiter_webhook_register"));
        assertTrue(names.contains("recruiter_audit_log_query"));
        System.out.println("✅ tools/list — 18 agents: PASS");
    }

    // ── Agent Tests ───────────────────────────────────────────────────────────

    @Test @Order(4)
    void testAccountOnboard() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{
              "name":"recruiter_account_onboard",
              "arguments":{
                "email":"recruiter@company.com",
                "first_name":"Jane","last_name":"Smith",
                "company_id":"company-abc",
                "subscription_tier":"professional",
                "jwt_token":"test.jwt.token"
              }}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("recruiter_id").asText().startsWith("rec-"));
        assertEquals("professional", c.get("subscription_tier").asText());
        assertNotNull(c.get("kafka_event").get("event_id").asText());
        assertEquals("recruiter.created", c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ recruiter_account_onboard: PASS");
    }

    @Test @Order(5)
    void testAccountOnboard_InvalidEmail_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{
              "name":"recruiter_account_onboard",
              "arguments":{
                "email":"not-an-email","first_name":"X","last_name":"Y",
                "company_id":"co-1","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "Invalid email should be rejected");
        System.out.println("✅ email validation guard: PASS");
    }

    @Test @Order(6)
    void testAccountOnboard_InvalidTier_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{
              "name":"recruiter_account_onboard",
              "arguments":{
                "email":"r@co.com","first_name":"A","last_name":"B",
                "company_id":"co-1","subscription_tier":"hacker_tier",
                "jwt_token":"t"}}}""");
        assertTrue(isError(resp), "Invalid tier must be rejected");
        System.out.println("✅ subscription tier validation: PASS");
    }

    @Test @Order(7)
    void testProfileGet() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{
              "name":"recruiter_profile_get",
              "arguments":{"recruiter_id":"rec-abc123","jwt_token":"test.token"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("cache_key").asText().contains("rec-abc123"));
        System.out.println("✅ recruiter_profile_get: PASS");
    }

    @Test @Order(8)
    void testDashboardGet() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{
              "name":"recruiter_dashboard_get",
              "arguments":{"recruiter_id":"rec-abc123","jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertNotNull(c.get("metrics").get("active_jobs"));
        assertEquals(300, c.get("cache_ttl_sec").asInt());
        System.out.println("✅ recruiter_dashboard_get: PASS");
    }

    @Test @Order(9)
    void testApplicationsList_WithFilters() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{
              "name":"recruiter_applications_list",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "status_filter":"assessed",
                "page_size":10,
                "jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertEquals("assessed", c.get("status_filter").asText());
        assertEquals(10, c.get("page_size").asInt());
        System.out.println("✅ recruiter_applications_list: PASS");
    }

    @Test @Order(10)
    void testApplicationsList_InvalidStatus_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{
              "name":"recruiter_applications_list",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "status_filter":"hacked_status",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "Invalid status filter must be rejected");
        System.out.println("✅ status filter validation: PASS");
    }

    @Test @Order(11)
    void testCandidateSave() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{
              "name":"recruiter_candidate_save",
              "arguments":{
                "recruiter_id":"rec-abc123","candidate_id":"cand-xyz456",
                "tag":"shortlist","note":"Strong technical background",
                "jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertEquals("shortlist", c.get("tag").asText());
        assertTrue(c.get("cache_invalidated").asText().contains("rec-abc123"));
        System.out.println("✅ recruiter_candidate_save: PASS");
    }

    @Test @Order(12)
    void testTeamInvite() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":12,"method":"tools/call","params":{
              "name":"recruiter_team_invite",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "invitee_email":"manager@company.com",
                "role":"HIRING_MANAGER",
                "jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("invite_token").asText().startsWith("inv-"));
        assertEquals("HIRING_MANAGER", c.get("role").asText());
        assertEquals("recruiter.team.member.invited", c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ recruiter_team_invite: PASS");
    }

    @Test @Order(13)
    void testTeamInvite_InvalidRole_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":13,"method":"tools/call","params":{
              "name":"recruiter_team_invite",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "invitee_email":"x@y.com","role":"SUPER_ADMIN",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "Invalid role must be rejected");
        System.out.println("✅ team invite role validation: PASS");
    }

    @Test @Order(14)
    void testWebhookRegister_ValidHttps() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":14,"method":"tools/call","params":{
              "name":"recruiter_webhook_register",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "endpoint_url":"https://external-ats.company.com/webhook",
                "event_type":"candidate_hired",
                "jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertTrue(c.get("webhook_id").asText().startsWith("wh-"));
        assertTrue(c.get("signing_secret").asText().startsWith("whs-"));
        System.out.println("✅ recruiter_webhook_register (HTTPS): PASS");
    }

    @Test @Order(15)
    void testWebhookRegister_HttpUrl_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":15,"method":"tools/call","params":{
              "name":"recruiter_webhook_register",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "endpoint_url":"http://insecure.example.com/hook",
                "event_type":"candidate_hired",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "HTTP webhook URL should be rejected");
        System.out.println("✅ webhook HTTP rejection (SSRF guard): PASS");
    }

    @Test @Order(16)
    void testWebhookRegister_InternalIp_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":16,"method":"tools/call","params":{
              "name":"recruiter_webhook_register",
              "arguments":{
                "recruiter_id":"rec-abc123",
                "endpoint_url":"https://192.168.1.1/internal",
                "event_type":"candidate_hired",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "Internal IP webhook URL must be rejected (SSRF)");
        System.out.println("✅ webhook SSRF (private IP) guard: PASS");
    }

    @Test @Order(17)
    void testSubscriptionUpgrade() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":17,"method":"tools/call","params":{
              "name":"recruiter_subscription_upgrade",
              "arguments":{
                "recruiter_id":"rec-abc123","new_tier":"enterprise",
                "billing_cycle":"annual","jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertEquals("enterprise", c.get("new_tier").asText());
        assertEquals("recruiter.subscription.upgraded", c.get("kafka_event").get("event_type").asText());
        System.out.println("✅ recruiter_subscription_upgrade: PASS");
    }

    @Test @Order(18)
    void testSubscriptionUpgrade_ToStarter_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":18,"method":"tools/call","params":{
              "name":"recruiter_subscription_upgrade",
              "arguments":{
                "recruiter_id":"rec-abc123","new_tier":"starter",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "Upgrading to starter tier should be rejected");
        System.out.println("✅ subscription upgrade to starter guard: PASS");
    }

    @Test @Order(19)
    void testAnalyticsGet() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":19,"method":"tools/call","params":{
              "name":"recruiter_analytics_get",
              "arguments":{
                "recruiter_id":"rec-abc123","metric_type":"all",
                "date_from":"2026-01-01","date_to":"2026-03-31",
                "jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertNotNull(c.get("application_funnel"));
        assertNotNull(c.get("time_to_hire"));
        assertNotNull(c.get("assessment_pass_rates"));
        System.out.println("✅ recruiter_analytics_get: PASS");
    }

    @Test @Order(20)
    void testAnalyticsGet_BadDateFormat_Rejected() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":20,"method":"tools/call","params":{
              "name":"recruiter_analytics_get",
              "arguments":{
                "recruiter_id":"rec-abc123","metric_type":"funnel",
                "date_from":"01-01-2026",
                "jwt_token":"tok"}}}""");
        assertTrue(isError(resp), "Bad date format should be rejected");
        System.out.println("✅ analytics date format validation: PASS");
    }

    @Test @Order(21)
    void testAuditLogQuery() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":21,"method":"tools/call","params":{
              "name":"recruiter_audit_log_query",
              "arguments":{
                "recruiter_id":"rec-abc123","action_filter":"ALL",
                "severity":"ALL","limit":50,"jwt_token":"tok"}}}""");
        JsonNode c = content(resp);
        assertEquals("success", c.get("status").asText());
        assertEquals(3, c.get("compliance").get("retention_years").asInt());
        assertTrue(c.get("compliance").get("dpdpa_2023").asBoolean());
        assertTrue(c.get("compliance").get("immutable").asBoolean());
        System.out.println("✅ recruiter_audit_log_query: PASS");
    }

    // ── Protocol Error Handling ───────────────────────────────────────────────

    @Test @Order(22)
    void testAllToolsHaveJwtTokenAndDescription() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"tools/list\",\"params\":{}}");
        for (JsonNode tool : mapper.readTree(resp).get("result").get("tools")) {
            String name = tool.get("name").asText();
            assertNotNull(tool.get("description"), name + " must have description");
            JsonNode props = tool.get("inputSchema").get("properties");
            assertNotNull(props, name + " must have properties");
            assertTrue(props.has("jwt_token"), name + " must have jwt_token");
        }
        System.out.println("✅ all 18 agents: description + jwt_token verified: PASS");
    }

    @Test @Order(23)
    void testMalformedJson_ParseError() throws Exception {
        String resp = server.dispatch("{malformed}");
        assertEquals(-32700, mapper.readTree(resp).get("error").get("code").asInt());
        System.out.println("✅ malformed JSON -32700: PASS");
    }

    @Test @Order(24)
    void testUnknownMethod() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"bad/method\",\"params\":{}}");
        assertEquals(-32601, mapper.readTree(resp).get("error").get("code").asInt());
        System.out.println("✅ unknown method -32601: PASS");
    }

    @Test @Order(25)
    void testPathTraversalInToolName() throws Exception {
        String resp = server.dispatch("{\"jsonrpc\":\"2.0\",\"id\":100,\"method\":\"tools/call\"," +
            "\"params\":{\"name\":\"../etc/passwd\",\"arguments\":{}}}");
        assertNotNull(mapper.readTree(resp).get("error"), "Path traversal must be rejected");
        System.out.println("✅ tool name injection guard: PASS");
    }

    @Test @Order(26)
    void testSqlInjectionInRecruiterId() throws Exception {
        String resp = server.dispatch("""
            {"jsonrpc":"2.0","id":101,"method":"tools/call","params":{
              "name":"recruiter_profile_get",
              "arguments":{"recruiter_id":"'; DROP TABLE recruiter;--","jwt_token":"t"}}}""");
        assertTrue(isError(resp), "SQL injection in recruiter_id must be rejected");
        System.out.println("✅ SQL injection in recruiter_id guard: PASS");
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
