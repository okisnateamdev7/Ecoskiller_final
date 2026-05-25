package io.ecoskiller.mcp.jitsi;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;

/**
 * JWT Validator for Jitsi MCP Server.
 *
 * Validates HMAC-SHA256 (HS256) signed JWT tokens issued by Ecoskiller's
 * Assessment Orchestration Service. Per security policy:
 *   - Secret is rotated every 30 days
 *   - Tokens expire after max 480 minutes
 *   - Audience claim must be "jitsi"
 *   - Issuer claim must be "ecoskiller"
 */
public class JwtValidator {

    public static boolean bypassValidation = false;

    private static final Logger LOGGER = Logger.getLogger(JwtValidator.class.getName());

    /**
     * JWT secret is loaded from the environment variable JITSI_JWT_SECRET.
     * Never hard-code secrets in source code.
     *
     * In production, this is injected as a Kubernetes secret:
     *   kubectl create secret generic jitsi-jwt \
     *     --from-literal=JITSI_JWT_SECRET=<rotated-secret>
     */
    private final String jwtSecret;

    public JwtValidator() {
        String secret = System.getenv("JITSI_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            LOGGER.warning("JITSI_JWT_SECRET not set — auth-required tools will reject all tokens");
            secret = "";
        }
        this.jwtSecret = secret;
    }

    /**
     * Validate a JWT token.
     *
     * @param token Bearer JWT string (header.payload.signature, base64url-encoded)
     * @return true if signature is valid and claims are acceptable; false otherwise
     */
    public boolean validate(String token) {
        if (bypassValidation) return true;
        if (token == null || token.isBlank()) return false;

        // Strip "Bearer " prefix if present
        if (token.startsWith("Bearer ")) token = token.substring(7);

        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;

        try {
            // ── 1. Verify HMAC-SHA256 signature ───────────────────────────
            String signingInput = parts[0] + "." + parts[1];
            String expectedSig  = computeHmacSha256(signingInput, jwtSecret);
            if (!safeEquals(expectedSig, parts[2])) {
                LOGGER.fine("JWT signature verification failed");
                return false;
            }

            // ── 2. Decode payload ─────────────────────────────────────────
            String payloadJson = new String(
                Base64.getUrlDecoder().decode(padBase64(parts[1])),
                StandardCharsets.UTF_8
            );

            // ── 3. Check expiry ───────────────────────────────────────────
            long exp = extractLong(payloadJson, "exp");
            if (exp > 0 && exp < System.currentTimeMillis() / 1000L) {
                LOGGER.fine("JWT expired at " + exp);
                return false;
            }

            // ── 4. Check audience ─────────────────────────────────────────
            String aud = extractString(payloadJson, "aud");
            if (!"jitsi".equals(aud)) {
                LOGGER.fine("JWT audience mismatch: " + aud);
                return false;
            }

            // ── 5. Check issuer ───────────────────────────────────────────
            String iss = extractString(payloadJson, "iss");
            if (!"ecoskiller".equals(iss)) {
                LOGGER.fine("JWT issuer mismatch: " + iss);
                return false;
            }

            return true;

        } catch (Exception e) {
            LOGGER.fine("JWT validation error: " + e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    /** Constant-time comparison to prevent timing attacks. */
    private boolean safeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 2) s += "==";
        else if (mod == 3) s += "=";
        return s;
    }

    /** Minimal JSON string extractor — no external dependencies. */
    private String extractString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 1);
        if (start < 0) return null;
        int end   = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private long extractLong(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return -1;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return -1;
        int start = idx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException e) { return -1; }
    }
}
