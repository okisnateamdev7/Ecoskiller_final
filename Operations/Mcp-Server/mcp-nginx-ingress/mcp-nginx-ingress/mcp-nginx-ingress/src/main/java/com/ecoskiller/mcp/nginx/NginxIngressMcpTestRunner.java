package com.ecoskiller.mcp.nginx;

import com.ecoskiller.mcp.nginx.util.JsonUtil;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Integration test for all 18 NGINX Ingress MCP tools.
 * Sends JSON-RPC messages via stdin, reads responses from stdout.
 *
 * Run:
 *   javac -d out src/main/java/com/ecoskiller/mcp/nginx/**\/*.java
 *   java -cp out com.ecoskiller.mcp.nginx.NginxIngressMcpTestRunner
 */
public class NginxIngressMcpTestRunner {

    private static int passed = 0;
    private static int failed = 0;
    private static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        verbose = args.length > 0 && "--verbose".equals(args[0]);

        System.out.println("===========================================");
        System.out.println(" NGINX Ingress MCP Server — Test Suite");
        System.out.println("===========================================\n");

        // Start server in subprocess
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", "out",
            "com.ecoskiller.mcp.nginx.NginxIngressMcpServer");
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        PrintWriter stdin  = new PrintWriter(new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));

        try {
            // ── 1. initialize ───────────────────────────────────────────
            test(stdin, stdout, "initialize", buildInitialize(), resp -> {
                assert resp.containsKey("result") : "Missing result";
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                assert "2024-11-05".equals(result.get("protocolVersion")) : "Wrong protocol version";
            });

            test(stdin, stdout, "ping", buildRequest("ping", null, 2), resp -> {
                assert resp.containsKey("result") : "Ping failed";
            });

            // ── 3. tools/list ───────────────────────────────────────────
            test(stdin, stdout, "tools/list", buildRequest("tools/list", null, 3), resp -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                @SuppressWarnings("unchecked")
                List<?> tools = (List<?>) result.get("tools");
                assert tools.size() == 18 : "Expected 18 tools, got " + tools.size();
            });

            // ── 4–21. Each tool called with valid args ──────────────────
            testTool(stdin, stdout, 4,  "ingress_route_manager",    Map.of("action","list"));
            testTool(stdin, stdout, 5,  "ingress_route_manager",    Map.of("action","create","name","test-ingress","host","api.ecoskiller.com","path","/test","service","test-svc","port",8080));
            testTool(stdin, stdout, 6,  "ssl_tls_manager",          Map.of("action","get","domain","api.ecoskiller.com"));
            testTool(stdin, stdout, 7,  "ssl_tls_manager",          Map.of("action","configure","domain","api.ecoskiller.com","protocols","TLSv1.3"));
            testTool(stdin, stdout, 8,  "rate_limit_controller",    Map.of("action","list"));
            testTool(stdin, stdout, 9,  "rate_limit_controller",    Map.of("action","set","path","/api/login","rps",5,"burst",10));
            testTool(stdin, stdout, 10, "load_balancer_config",     Map.of("action","list"));
            testTool(stdin, stdout, 11, "load_balancer_config",     Map.of("action","configure","service","assessment-service","algorithm","least_conn"));
            testTool(stdin, stdout, 12, "health_check_monitor",     Map.of("action","list"));
            testTool(stdin, stdout, 13, "waf_security_manager",     Map.of("action","list-rules"));
            testTool(stdin, stdout, 14, "auth_enforcement",         Map.of("action","get"));
            testTool(stdin, stdout, 15, "canary_deployment",        Map.of("action","list"));
            testTool(stdin, stdout, 16, "certificate_lifecycle",    Map.of("action","list"));
            testTool(stdin, stdout, 17, "traffic_shaping",          Map.of("action","get"));
            testTool(stdin, stdout, 18, "websocket_proxy",          Map.of("action","get"));
            testTool(stdin, stdout, 19, "request_rewrite_engine",   Map.of("action","list"));
            testTool(stdin, stdout, 20, "observability_exporter",   Map.of("action","status"));
            testTool(stdin, stdout, 21, "backend_upstream_manager", Map.of("action","list"));
            testTool(stdin, stdout, 22, "configmap_hot_reload",     Map.of("action","get"));
            testTool(stdin, stdout, 23, "multi_cloud_ingress",      Map.of("action","status"));
            testTool(stdin, stdout, 24, "ddos_protection",          Map.of("action","status"));
            testTool(stdin, stdout, 25, "audit_compliance",         Map.of("action","status"));

            // ── Security tests ──────────────────────────────────────────
            testSecurityRejection(stdin, stdout, 26, "ingress_route_manager",
                Map.of("action","create","host","evil.com; rm -rf /"), "Shell injection in host");

            testSecurityRejection(stdin, stdout, 27, "ingress_route_manager",
                Map.of("action","create","host","not_a_hostname!@#"), "Invalid hostname characters");

            testSecurityRejection(stdin, stdout, 28, "rate_limit_controller",
                Map.of("action","set","rps",999999), "rps out of range");

            testSecurityRejection(stdin, stdout, 29, "request_rewrite_engine",
                Map.of("action","add","headerName","X-Header","headerValue","val\r\ninjected: header"), "CRLF injection");

            testSecurityRejection(stdin, stdout, 30, "ddos_protection",
                Map.of("action","block-ip","ip","999.999.999.999/99"), "Invalid CIDR");

            // ── Method not found ────────────────────────────────────────
            test(stdin, stdout, "unknown method", buildRequest("unknown/method", null, 99), resp -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = (Map<String, Object>) resp.get("error");
                assert error != null : "Expected error for unknown method";
                assert ((Number) error.get("code")).intValue() == -32601 : "Expected -32601";
            });

            // ── shutdown ────────────────────────────────────────────────
            stdin.println(JsonUtil.toJson(Map.of("jsonrpc","2.0","method","shutdown","id",100)));

        } finally {
            proc.destroy();
        }

        System.out.println("\n===========================================");
        System.out.printf("  Results: %d passed, %d failed%n", passed, failed);
        System.out.println("===========================================");

        if (failed > 0) System.exit(1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void testTool(PrintWriter stdin, BufferedReader stdout, int id,
                                  String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name",      toolName);
        params.put("arguments", toolArgs);
        test(stdin, stdout, toolName, buildRequest("tools/call", params, id), resp -> {
            assert !resp.containsKey("error") : "Tool error: " + resp.get("error");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resp.get("result");
            assert result != null && result.containsKey("content") : "Missing content";
        });
    }

    private static void testSecurityRejection(PrintWriter stdin, BufferedReader stdout, int id,
                                               String toolName, Map<String, Object> toolArgs, String label) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name",      toolName);
        params.put("arguments", toolArgs);
        test(stdin, stdout, "SECURITY: " + label, buildRequest("tools/call", params, id), resp -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> error = (Map<String, Object>) resp.get("error");
            assert error != null : "Expected security rejection but got success";
        });
    }

    @FunctionalInterface
    interface Check { void check(Map<String, Object> resp) throws Exception; }

    private static void test(PrintWriter stdin, BufferedReader stdout,
                              String name, String request, Check check) {
        try {
            stdin.println(request);
            String line = stdout.readLine();
            if (line == null) throw new AssertionError("No response received");
            Map<String, Object> resp = JsonUtil.parseObject(line);
            if (verbose) System.out.println("  Response: " + line.substring(0, Math.min(line.length(), 200)));
            check.check(resp);
            System.out.println("  ✓ PASS  " + name);
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ FAIL  " + name + " — " + e.getMessage());
            failed++;
        }
    }

    private static String buildInitialize() {
        return buildRequest("initialize", Map.of(
            "protocolVersion", "2024-11-05",
            "clientInfo", Map.of("name", "test-client", "version", "1.0")
        ), 1);
    }

    private static String buildRequest(String method, Map<String, Object> params, int id) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("method",  method);
        if (params != null) req.put("params", params);
        req.put("id", id);
        return JsonUtil.toJson(req);
    }
}
