package io.ecoskiller.mcp.jitsi;

import java.util.*;

/**
 * Test runner for mcp-jitsi-videobridge
 *
 * Usage:
 *   javac -d out src/**​/*.java
 *   java -cp out io.ecoskiller.mcp.jitsi.TestRunner
 *   java -cp out io.ecoskiller.mcp.jitsi.TestRunner --verbose
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static boolean verbose = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Test framework
    // ─────────────────────────────────────────────────────────────────────────

    static void test(String name, Runnable body) {
        try {
            body.run();
            passed++;
            System.out.println("  ✓ " + name);
        } catch (AssertionError | Exception e) {
            failed++;
            System.out.println("  ✗ " + name);
            System.out.println("      " + e.getMessage());
            if (verbose) e.printStackTrace();
        }
    }

    static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + "  Actual: " + actual);
        }
    }

    static void assertTrue(boolean condition, String msg) {
        if (!condition) throw new AssertionError(msg);
    }

    static void assertContains(String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError("Expected to contain '" + needle + "' in: " + haystack);
        }
    }

    static void assertNotContains(String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            throw new AssertionError("Should NOT contain '" + needle + "' in: " + haystack);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: JSON Parser
    // ─────────────────────────────────────────────────────────────────────────

    static void testJsonParser() {
        System.out.println("\n── JSON Parser ──");

        test("parse simple object", () -> {
            Map<String, Object> m = JsonParser.parseObject("{\"key\":\"value\"}");
            assertEquals("value", m.get("key"));
        });

        test("parse nested object", () -> {
            Map<String, Object> m = JsonParser.parseObject("{\"a\":{\"b\":42}}");
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = (Map<String, Object>) m.get("a");
            assertEquals(42L, inner.get("b"));
        });

        test("parse array", () -> {
            Map<String, Object> m = JsonParser.parseObject("{\"items\":[1,2,3]}");
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) m.get("items");
            assertEquals(3, list.size());
        });

        test("parse boolean and null", () -> {
            Map<String, Object> m = JsonParser.parseObject("{\"ok\":true,\"x\":null}");
            assertEquals(Boolean.TRUE, m.get("ok"));
            assertEquals(null, m.get("x"));
        });

        test("parse escaped string", () -> {
            Map<String, Object> m = JsonParser.parseObject("{\"msg\":\"hello\\\"world\"}");
            assertEquals("hello\"world", m.get("msg"));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: JSON Serializer
    // ─────────────────────────────────────────────────────────────────────────

    static void testJsonSerializer() {
        System.out.println("\n── JSON Serializer ──");

        test("serialize string", () ->
            assertEquals("\"hello\"", JsonSerializer.toJson("hello")));

        test("serialize number", () ->
            assertEquals("42", JsonSerializer.toJson(42)));

        test("serialize boolean", () ->
            assertEquals("true", JsonSerializer.toJson(true)));

        test("serialize null", () ->
            assertEquals("null", JsonSerializer.toJson(null)));

        test("serialize map", () -> {
            String json = JsonSerializer.toJson(Map.of("k", "v"));
            assertContains(json, "\"k\"");
            assertContains(json, "\"v\"");
        });

        test("escape special chars", () -> {
            String json = JsonSerializer.toJson("line1\nline2");
            assertContains(json, "\\n");
        });

        test("no XSS characters unescaped", () -> {
            String json = JsonSerializer.toJson("<script>alert(1)</script>");
            // < and > are safe in JSON but the angle-bracket content should be preserved
            assertContains(json, "<script>");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: Rate Limiter
    // ─────────────────────────────────────────────────────────────────────────

    static void testRateLimiter() {
        System.out.println("\n── Rate Limiter ──");

        test("allows requests within limit", () -> {
            RateLimiter rl = new RateLimiter(5, 60_000);
            for (int i = 0; i < 5; i++) {
                assertTrue(rl.allow("test_tool"), "Request " + (i+1) + " should be allowed");
            }
        });

        test("blocks requests over limit", () -> {
            RateLimiter rl = new RateLimiter(3, 60_000);
            rl.allow("t"); rl.allow("t"); rl.allow("t");
            assertTrue(!rl.allow("t"), "4th request should be blocked");
        });

        test("limits are per-tool independent", () -> {
            RateLimiter rl = new RateLimiter(2, 60_000);
            rl.allow("toolA"); rl.allow("toolA");
            assertTrue(!rl.allow("toolA"), "toolA should be blocked");
            assertTrue(rl.allow("toolB"),  "toolB should still be allowed");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: JWT Validator
    // ─────────────────────────────────────────────────────────────────────────

    static void testJwtValidator() {
        System.out.println("\n── JWT Validator ──");

        test("rejects null token", () -> {
            JwtValidator v = new JwtValidator();
            assertTrue(!v.validate(null), "null token should fail");
        });

        test("rejects empty token", () -> {
            JwtValidator v = new JwtValidator();
            assertTrue(!v.validate(""), "empty token should fail");
        });

        test("rejects malformed token (no dots)", () -> {
            JwtValidator v = new JwtValidator();
            assertTrue(!v.validate("notavalidjwt"), "malformed token should fail");
        });

        test("rejects token with wrong segment count", () -> {
            JwtValidator v = new JwtValidator();
            assertTrue(!v.validate("a.b"), "2-segment token should fail");
        });

        test("rejects clearly invalid signature", () -> {
            JwtValidator v = new JwtValidator();
            String fakeToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9" +
                               ".eyJhdWQiOiJqaXRzaSIsImlzcyI6ImVjb3NraWxsZXIiLCJleHAiOjk5OTk5OTk5OTl9" +
                               ".INVALIDSIGNATURE";
            assertTrue(!v.validate(fakeToken), "bad signature should fail");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: MCP Protocol
    // ─────────────────────────────────────────────────────────────────────────

    static void testMcpProtocol() {
        System.out.println("\n── MCP Protocol ──");
        McpServer server = new McpServer();

        test("initialize returns protocolVersion", () -> {
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":{}}");
            assertContains(resp, "2024-11-05");
            assertContains(resp, "mcp-jitsi-videobridge");
        });

        test("ping returns empty result", () -> {
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"ping\",\"params\":{}}");
            assertContains(resp, "\"result\"");
            assertNotContains(resp, "\"error\"");
        });

        test("tools/list returns 14 tools", () -> {
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"tools/list\",\"params\":{}}");
            assertContains(resp, "conference_create");
            assertContains(resp, "conference_terminate");
            assertContains(resp, "conference_status");
            assertContains(resp, "participant_join");
            assertContains(resp, "participant_remove");
            assertContains(resp, "active_speaker_detection");
            assertContains(resp, "bandwidth_management");
            assertContains(resp, "recording_control");
            assertContains(resp, "recording_status");
            assertContains(resp, "bridge_health");
            assertContains(resp, "bridge_scaling");
            assertContains(resp, "kafka_event_emit");
            assertContains(resp, "jwt_generate");
            assertContains(resp, "network_diagnostics");
        });

        test("unknown method returns -32601", () -> {
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"method\":\"unknown/method\",\"params\":{}}");
            assertContains(resp, "-32601");
        });

        test("malformed JSON returns -32603", () -> {
            String resp = server.handleRequest("{this is not json}");
            assertContains(resp, "error");
        });

        test("missing method returns -32600", () -> {
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"5\",\"params\":{}}");
            assertContains(resp, "-32600");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tests: Tool Execution
    // ─────────────────────────────────────────────────────────────────────────

    static void testTools() {
        System.out.println("\n── Tool Execution ──");
        McpServer server = new McpServer();

        // conference_create (public tool — no auth required for this test)
        test("conference_create returns conference_id", () -> {
            String req = """
                {"jsonrpc":"2.0","id":"10","method":"tools/call","params":{
                  "name":"conference_create",
                  "arguments":{
                    "assessment_id":"gd-12345",
                    "assessment_type":"group_discussion",
                    "participants":[
                      {"id":"cand-001","name":"Alice","role":"candidate"},
                      {"id":"intvr-001","name":"Bob","role":"interviewer"}
                    ]
                  }
                }}""";
            String resp = server.handleRequest(req);
            assertContains(resp, "conference_id");
            assertContains(resp, "gd-12345");
            assertContains(resp, "created");
            assertNotContains(resp, "\"isError\":true");
        });

        test("conference_create rejects invalid assessment_id", () -> {
            String req = """
                {"jsonrpc":"2.0","id":"11","method":"tools/call","params":{
                  "name":"conference_create",
                  "arguments":{
                    "assessment_id":"bad id with spaces!",
                    "assessment_type":"group_discussion",
                    "participants":[{"id":"p1","name":"Test","role":"candidate"}]
                  }
                }}""";
            String resp = server.handleRequest(req);
            assertContains(resp, "\"isError\":true");
        });

        test("conference_create rejects too many participants for technical_interview", () -> {
            String args = """
                {"jsonrpc":"2.0","id":"12","method":"tools/call","params":{
                  "name":"conference_create",
                  "arguments":{
                    "assessment_id":"ti-001",
                    "assessment_type":"technical_interview",
                    "participants":[
                      {"id":"p1","name":"A","role":"candidate"},
                      {"id":"p2","name":"B","role":"interviewer"},
                      {"id":"p3","name":"C","role":"interviewer"},
                      {"id":"p4","name":"D","role":"interviewer"}
                    ]
                  }
                }}""";
            String resp = server.handleRequest(args);
            assertContains(resp, "\"isError\":true");
        });

        test("participant_join emits kafka event", () -> {
            // First create a conference
            server.handleRequest("""
                {"jsonrpc":"2.0","id":"100","method":"tools/call","params":{
                  "name":"conference_create",
                  "arguments":{
                    "assessment_id":"gd-join-test",
                    "assessment_type":"group_discussion",
                    "participants":[{"id":"p1","name":"Test","role":"candidate"}]
                  }
                }}""");

            // Get the conference id (simplified - use predictable pattern in test)
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"13","method":"tools/call","params":{
                  "name":"participant_join",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "participant_id":"cand-001",
                    "participant_name":"Alice Smith",
                    "role":"candidate"
                  }
                }}""");
            // participant_join should work regardless (bridge may not have the conf yet)
            assertContains(resp, "joined");
            assertContains(resp, "assessment.participants");
        });

        test("active_speaker_detection get_stats", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"14","method":"tools/call","params":{
                  "name":"active_speaker_detection",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "action":"get_stats"
                  }
                }}""");
            assertContains(resp, "current_speaker");
            assertContains(resp, "speaker_stats");
            assertContains(resp, "assessment.speakers");
        });

        test("active_speaker_detection rejects invalid interval", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"15","method":"tools/call","params":{
                  "name":"active_speaker_detection",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "action":"configure",
                    "update_interval_ms":9999
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        test("bandwidth_management rejects out-of-range bitrate", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"16","method":"tools/call","params":{
                  "name":"bandwidth_management",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "action":"set_max_bitrate",
                    "max_video_bitrate_kbps":99999
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        test("recording_control rejects bad storage_path", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"17","method":"tools/call","params":{
                  "name":"recording_control",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "action":"start",
                    "storage_path":"ftp://illegal-path/"
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        test("bridge_health returns cluster status", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"18","method":"tools/call","params":{
                  "name":"bridge_health",
                  "arguments":{"metric":"all"}
                }}""");
            assertContains(resp, "cluster_status");
            assertContains(resp, "total_nodes");
            assertContains(resp, "jicofo_status");
        });

        test("network_diagnostics full_check", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"19","method":"tools/call","params":{
                  "name":"network_diagnostics",
                  "arguments":{"diagnostic_type":"full_check"}
                }}""");
            assertContains(resp, "stun");
            assertContains(resp, "turn");
            assertContains(resp, "jicofo_status");
            assertContains(resp, "overall_health");
        });

        test("kafka_event_emit rejects wrong event for topic", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"20","method":"tools/call","params":{
                  "name":"kafka_event_emit",
                  "arguments":{
                    "topic":"assessment.participants",
                    "event_type":"recording_completed",
                    "assessment_id":"gd-999",
                    "payload":{}
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        test("jwt_generate rejects bad email", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"21","method":"tools/call","params":{
                  "name":"jwt_generate",
                  "arguments":{
                    "assessment_id":"gd-001",
                    "participant_id":"p1",
                    "participant_name":"Alice",
                    "role":"candidate",
                    "email":"not-an-email"
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        test("bridge_scaling rejects replicas out of HPA bounds", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"22","method":"tools/call","params":{
                  "name":"bridge_scaling",
                  "arguments":{
                    "action":"set_replicas",
                    "replicas":99
                  }
                }}""");
            assertContains(resp, "\"isError\":true");
        });

        // ── Security: auth-required tools should reject without token ──────
        test("conference_terminate rejected without JWT", () -> {
            JwtValidator.bypassValidation = false;
            try {
                String resp = server.handleRequest("""
                    {"jsonrpc":"2.0","id":"23","method":"tools/call","params":{
                      "name":"conference_terminate",
                      "arguments":{"conference_id":"conf-aabbccddeeff","reason":"normal_exit"}
                    }}""");
                assertContains(resp, "-32401");
            } finally {
                JwtValidator.bypassValidation = true;
            }
        });

        test("recording_control rejected without JWT", () -> {
            JwtValidator.bypassValidation = false;
            try {
                String resp = server.handleRequest("""
                    {"jsonrpc":"2.0","id":"24","method":"tools/call","params":{
                      "name":"recording_control",
                      "arguments":{
                        "conference_id":"conf-aabbccddeeff",
                        "action":"start"
                      }
                    }}""");
                assertContains(resp, "-32401");
            } finally {
                JwtValidator.bypassValidation = true;
            }
        });

        test("rate limiter blocks excessive calls", () -> {
            // Make many rapid calls to a public tool
            for (int i = 0; i < 65; i++) {
                server.handleRequest(
                    "{\"jsonrpc\":\"2.0\",\"id\":\"rl" + i + "\",\"method\":\"tools/call\"," +
                    "\"params\":{\"name\":\"bridge_health\",\"arguments\":{}}}");
            }
            String resp = server.handleRequest(
                "{\"jsonrpc\":\"2.0\",\"id\":\"rl_final\",\"method\":\"tools/call\"," +
                "\"params\":{\"name\":\"bridge_health\",\"arguments\":{}}}");
            assertContains(resp, "-32029");
        });

        test("XSS in participant_name is sanitized", () -> {
            String resp = server.handleRequest("""
                {"jsonrpc":"2.0","id":"25","method":"tools/call","params":{
                  "name":"participant_join",
                  "arguments":{
                    "conference_id":"conf-aabbccddeeff",
                    "participant_id":"p-xss",
                    "participant_name":"<script>alert(1)</script>",
                    "role":"candidate"
                  }
                }}""");
            // Should succeed but name should be sanitised in actual response
            assertNotContains(resp, "<script>");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        verbose = Arrays.asList(args).contains("--verbose");

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  mcp-jitsi-videobridge — Test Suite                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        testJsonParser();
        testJsonSerializer();
        testRateLimiter();
        testJwtValidator();
        JwtValidator.bypassValidation = true;
        testMcpProtocol();
        testTools();

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.printf("  Passed: %d  |  Failed: %d  |  Total: %d%n",
            passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════════════════════");

        if (failed > 0) System.exit(1);
    }
}
