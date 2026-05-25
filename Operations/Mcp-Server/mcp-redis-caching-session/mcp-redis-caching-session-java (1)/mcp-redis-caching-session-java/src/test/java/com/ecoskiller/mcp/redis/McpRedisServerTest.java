package com.ecoskiller.mcp.redis;

import com.ecoskiller.mcp.redis.config.RedisConfig;
import com.ecoskiller.mcp.redis.security.AuditLogger;
import com.ecoskiller.mcp.redis.tools.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Redis MCP Server.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpRedisServerTest {

    private static final String TEST_API_KEY = "test-secret-key-ecoskiller";
    private static final ObjectMapper JSON    = new ObjectMapper();

    private static RedisServer  redisServer;
    private static RedisConfig  cfg;
    private static AuditLogger  audit;
    private static ToolRegistry registry;

    @BeforeAll
    static void setup() throws Exception {
        // Start an in-memory Redis mock server
        redisServer = RedisServer.newRedisServer();
        redisServer.start();

        // Configure system properties to point to the mock Redis
        System.setProperty("REDIS_HOST", "localhost");
        System.setProperty("REDIS_PORT", String.valueOf(redisServer.getBindPort()));
        System.setProperty("MCP_API_KEY", TEST_API_KEY);

        cfg      = RedisConfig.fromEnvironment();
        audit    = new AuditLogger();
        registry = new ToolRegistry(cfg, audit);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (cfg != null) {
            cfg.close();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helper: build args node
    // ────────────────────────────────────────────────────────────────────────

    private ObjectNode args(String... kvPairs) {
        ObjectNode n = JSON.createObjectNode();
        n.put("api_key", TEST_API_KEY);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            n.put(kvPairs[i], kvPairs[i + 1]);
        }
        return n;
    }

    private ToolResult call(String toolName, ObjectNode arguments) throws Exception {
        McpTool tool = registry.find(toolName);
        assertNotNull(tool, "Tool not found: " + toolName);
        return tool.execute(arguments);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Registry
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(1)
    void testRegistryHas20Tools() {
        assertEquals(21, registry.size(), "Should have exactly 21 tools");
    }

    @Test @Order(2)
    void testAllToolsHaveSchemas() {
        for (McpTool tool : registry.allTools()) {
            ObjectNode schema = tool.schema();
            assertNotNull(schema.get("name"),        "Tool " + tool.name() + " missing name");
            assertNotNull(schema.get("description"), "Tool " + tool.name() + " missing description");
            assertNotNull(schema.get("inputSchema"), "Tool " + tool.name() + " missing inputSchema");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // JSON-RPC protocol tests
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(3)
    void testInitializeProtocol() throws Exception {
        McpRedisServer server = new McpRedisServer();

        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        // Use reflection to call handleRequest for unit test
        java.lang.reflect.Method m = McpRedisServer.class
                .getDeclaredMethod("handleRequest", JsonNode.class);
        m.setAccessible(true);
        String resp = (String) m.invoke(server, JSON.readTree(req));

        JsonNode r = JSON.readTree(resp);
        assertEquals("2.0",        r.path("jsonrpc").asText());
        assertEquals(1,            r.path("id").asInt());
        assertEquals("2024-11-05", r.path("result").path("protocolVersion").asText());
        assertEquals("mcp-redis-caching-session", r.path("result").path("serverInfo").path("name").asText());
    }

    @Test @Order(4)
    void testToolsListProtocol() throws Exception {
        McpRedisServer server = new McpRedisServer();
        String req = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
        java.lang.reflect.Method m = McpRedisServer.class
                .getDeclaredMethod("handleRequest", JsonNode.class);
        m.setAccessible(true);
        String resp = (String) m.invoke(server, JSON.readTree(req));

        JsonNode r = JSON.readTree(resp);
        JsonNode tools = r.path("result").path("tools");
        assertTrue(tools.isArray());
        assertEquals(21, tools.size(), "tools/list should return 21 tools");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Cache operations
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(10)
    void testCacheSetAndGet() throws Exception {
        // SET
        ToolResult setResult = call("cache_set", args(
            "namespace", "candidate",
            "key",       "test-cand-001",
            "value",     "{\"name\":\"Test User\",\"score\":88.5}",
            "ttl_secs",  "60"
        ));
        assertFalse(setResult.isError(), "cache_set should not error");
        JsonNode setJson = JSON.readTree(setResult.content());
        assertTrue(setJson.path("success").asBoolean(), "cache_set success should be true");

        // GET
        ToolResult getResult = call("cache_get", args(
            "namespace", "candidate",
            "key",       "test-cand-001"
        ));
        assertFalse(getResult.isError());
        JsonNode getJson = JSON.readTree(getResult.content());
        assertTrue(getJson.path("hit").asBoolean(), "Should be cache hit");
        assertTrue(getJson.path("ttl_secs").asLong() > 0, "TTL should be positive");
        assertTrue(getJson.path("value").asText().contains("Test User"));
    }

    @Test @Order(11)
    void testCacheGetMiss() throws Exception {
        ToolResult r = call("cache_get", args(
            "namespace", "candidate",
            "key",       "nonexistent-key-xyz-99999"
        ));
        assertFalse(r.isError());
        JsonNode j = JSON.readTree(r.content());
        assertFalse(j.path("hit").asBoolean(), "Should be cache miss");
    }

    @Test @Order(12)
    void testCacheDelete() throws Exception {
        // Set a key
        call("cache_set", args("namespace", "test", "key", "del-me", "value", "hello"));

        // Delete it
        ToolResult r = call("cache_delete", args("namespace", "test", "key", "del-me"));
        assertFalse(r.isError());
        JsonNode j = JSON.readTree(r.content());
        assertTrue(j.path("deleted").asBoolean());

        // Verify gone
        ToolResult getR = call("cache_get", args("namespace", "test", "key", "del-me"));
        assertFalse(JSON.readTree(getR.content()).path("hit").asBoolean());
    }

    @Test @Order(13)
    void testCacheMGet() throws Exception {
        // Pre-populate
        call("cache_set", args("namespace", "multi", "key", "k1", "value", "v1"));
        call("cache_set", args("namespace", "multi", "key", "k2", "value", "v2"));

        ObjectNode mgetArgs = args("namespace", "multi");
        mgetArgs.putArray("keys").add("k1").add("k2").add("k3-missing");
        ToolResult r = call("cache_mget", mgetArgs);
        assertFalse(r.isError());
        JsonNode j = JSON.readTree(r.content());
        assertEquals(3, j.path("total").asInt());
        assertEquals(2, j.path("hits").asInt());
        assertEquals(1, j.path("misses").asInt());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Session management
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(20)
    void testSessionLifecycle() throws Exception {
        String token = "test-jwt-token-abc123";

        // Create
        ToolResult createR = call("session_create", args(
            "token",     token,
            "user_id",   "cand-uuid-001",
            "role",      "candidate",
            "email",     "test@ecoskiller.com",
            "tenant_id", "acme-corp"
        ));
        assertFalse(createR.isError());
        assertTrue(JSON.readTree(createR.content()).path("success").asBoolean());

        // Get
        ToolResult getR = call("session_get", args("token", token));
        assertFalse(getR.isError());
        JsonNode s = JSON.readTree(getR.content());
        assertTrue(s.path("valid").asBoolean());
        assertEquals("candidate",  s.path("role").asText());
        assertEquals("cand-uuid-001", s.path("user_id").asText());
        assertEquals("acme-corp",  s.path("tenant_id").asText());

        // Refresh
        ToolResult refreshR = call("session_refresh", args("token", token));
        assertFalse(refreshR.isError());
        assertTrue(JSON.readTree(refreshR.content()).path("success").asBoolean());

        // Destroy
        ToolResult destroyR = call("session_destroy", args("token", token));
        assertFalse(destroyR.isError());
        assertTrue(JSON.readTree(destroyR.content()).path("destroyed").asBoolean());

        // Verify gone
        ToolResult goneR = call("session_get", args("token", token));
        assertFalse(JSON.readTree(goneR.content()).path("valid").asBoolean());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Leaderboard
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(30)
    void testLeaderboard() throws Exception {
        String lb = "test-assessment-001";

        // Add members
        call("leaderboard_add", args("leaderboard_id", lb, "member", "cand-A", "score", "92.5"));
        call("leaderboard_add", args("leaderboard_id", lb, "member", "cand-B", "score", "85.0"));
        call("leaderboard_add", args("leaderboard_id", lb, "member", "cand-C", "score", "97.0"));

        // Get top-3
        ToolResult getR = call("leaderboard_get", args("leaderboard_id", lb, "limit", "3"));
        assertFalse(getR.isError());
        JsonNode entries = JSON.readTree(getR.content()).path("entries");
        assertEquals(3, entries.size());
        assertEquals("cand-C", entries.get(0).path("member").asText(), "cand-C should be rank 1");
        assertEquals(1, entries.get(0).path("rank").asInt());

        // Check individual rank
        ToolResult rankR = call("leaderboard_rank", args("leaderboard_id", lb, "member", "cand-A"));
        assertFalse(rankR.isError());
        JsonNode rankJ = JSON.readTree(rankR.content());
        assertTrue(rankJ.path("found").asBoolean());
        assertEquals(2, rankJ.path("rank").asInt(), "cand-A should be rank 2");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Rate limiting
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(40)
    void testRateLimit() throws Exception {
        // Reset first
        call("rate_limit_reset", args("scope", "test_submit", "identifier", "cand-rl-001"));

        // Allow up to limit
        for (int i = 1; i <= 3; i++) {
            ToolResult r = call("rate_limit_check", args(
                "scope",       "test_submit",
                "identifier",  "cand-rl-001",
                "limit",       "3",
                "window_secs", "60"
            ));
            JsonNode j = JSON.readTree(r.content());
            assertEquals(i, j.path("current_count").asInt());
            assertTrue(j.path("allowed").asBoolean(), "Request " + i + " should be allowed");
        }

        // 4th should be blocked
        ToolResult blocked = call("rate_limit_check", args(
            "scope",       "test_submit",
            "identifier",  "cand-rl-001",
            "limit",       "3",
            "window_secs", "60"
        ));
        assertFalse(JSON.readTree(blocked.content()).path("allowed").asBoolean(), "4th request should be blocked");
        assertEquals(429, JSON.readTree(blocked.content()).path("http_status").asInt());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Counters
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(50)
    void testCounters() throws Exception {
        call("counter_reset", args("scope", "test", "name", "assessments_done"));

        call("counter_increment", args("scope", "test", "name", "assessments_done"));
        call("counter_increment", args("scope", "test", "name", "assessments_done"));
        call("counter_increment", args("scope", "test", "name", "assessments_done", "by", "5"));

        ToolResult r = call("counter_get", args("scope", "test", "name", "assessments_done"));
        assertFalse(r.isError());
        assertEquals(7, JSON.readTree(r.content()).path("value").asLong());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Pub/Sub
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(60)
    void testPubSubPublish() throws Exception {
        ToolResult r = call("pubsub_publish", args(
            "channel",    "assessment:completed",
            "message",    "{\"candidate_id\":\"cand-001\",\"score\":88.5}",
            "event_type", "SCORE_UPDATED"
        ));
        assertFalse(r.isError());
        JsonNode j = JSON.readTree(r.content());
        assertTrue(j.path("published").asBoolean());
        assertEquals("assessment:completed", j.path("channel").asText());
    }

    @Test @Order(61)
    void testPubSubInvalidChannelRejected() {
        Exception exception = assertThrows(Exception.class, () -> {
            call("pubsub_publish", args(
                "channel", "invalid_prefix:something",
                "message", "test"
            ));
        });
        assertTrue(exception.getMessage().contains("not allowed") || exception.getMessage().contains("invalid_prefix"));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Distributed locks
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(70)
    void testDistributedLock() throws Exception {
        String resource = "royalty:calc-test-001";

        // Acquire
        ToolResult acquireR = call("lock_acquire", args("resource", resource, "ttl_secs", "10"));
        assertFalse(acquireR.isError());
        JsonNode acq = JSON.readTree(acquireR.content());
        assertTrue(acq.path("acquired").asBoolean(), "Should acquire lock");
        String lockToken = acq.path("lock_token").asText();
        assertFalse(lockToken.isEmpty(), "Lock token must be returned");

        // Second acquire should fail
        ToolResult acq2 = call("lock_acquire", args("resource", resource, "ttl_secs", "10"));
        assertFalse(JSON.readTree(acq2.content()).path("acquired").asBoolean(), "Second acquire should fail");

        // Release with wrong token
        ToolResult wrongRelease = call("lock_release", args("resource", resource, "lock_token", "wrong-token"));
        assertFalse(JSON.readTree(wrongRelease.content()).path("released").asBoolean(), "Wrong token should not release");

        // Release correctly
        ObjectNode releaseArgs = args("resource", resource);
        releaseArgs.put("lock_token", lockToken);
        ToolResult releaseR = call("lock_release", releaseArgs);
        assertTrue(JSON.readTree(releaseR.content()).path("released").asBoolean(), "Correct token should release");

        // Can re-acquire after release
        ToolResult acq3 = call("lock_acquire", args("resource", resource, "ttl_secs", "10"));
        assertTrue(JSON.readTree(acq3.content()).path("acquired").asBoolean(), "Should re-acquire after release");

        // Cleanup
        String tok3 = JSON.readTree(acq3.content()).path("lock_token").asText();
        ObjectNode cleanArgs = args("resource", resource);
        cleanArgs.put("lock_token", tok3);
        call("lock_release", cleanArgs);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Health
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(80)
    void testRedisHealth() throws Exception {
        ToolResult r = call("redis_health", args());
        assertFalse(r.isError());
        JsonNode j = JSON.readTree(r.content());
        assertTrue(j.path("ping_ok").asBoolean(), "Redis should respond to ping");
        assertNotNull(j.path("memory"), "Memory section should be present");
        assertNotNull(j.path("stats"),  "Stats section should be present");
        assertNotNull(j.path("alerts"), "Alerts section should be present");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Security: invalid key characters
    // ────────────────────────────────────────────────────────────────────────

    @Test @Order(90)
    void testKeyInjectionPrevented() {
        assertThrows(Exception.class, () ->
            call("cache_get", args("namespace", "candidate", "key", "key\nwith\nnewlines"))
        );
    }

    @Test @Order(91)
    void testFlushNamespaceRequiresConfirm() throws Exception {
        ToolResult r = call("cache_flush_namespace", args("namespace", "test", "confirm", "WRONG"));
        assertTrue(r.isError() || r.content().contains("Safety"));
    }
}
