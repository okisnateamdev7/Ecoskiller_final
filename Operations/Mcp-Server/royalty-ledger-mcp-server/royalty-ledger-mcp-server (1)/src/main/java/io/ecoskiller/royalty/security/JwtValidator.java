package io.ecoskiller.royalty.security;

import io.ecoskiller.royalty.config.ServerConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JwtValidator {
    private final String secret;
    private final ConcurrentHashMap<String, CachedResult> cache = new ConcurrentHashMap<>();
    private static final long TTL_MS = 5 * 60 * 1000L;

    public JwtValidator(ServerConfig c) { this.secret = c.getJwtSecret(); }

    public ValidationResult validate(String token, String requiredRole) {
        if (token == null || token.isBlank()) return ValidationResult.fail("Token is null or empty");
        String cacheKey = hashToken(token);
        CachedResult cached = cache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            if (cached.result.isValid()) {
                long exp = parseLong(cached.result.getClaim("exp"), 0);
                if (exp > 0 && Instant.now().getEpochSecond() > exp) { cache.remove(cacheKey); return ValidationResult.fail("Token expired (cache re-check)"); }
            }
            return cached.result;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) return store(cacheKey, ValidationResult.fail("Invalid JWT: expected 3 parts"));
        Map<String, String> header = decodeJson(parts[0]);
        if (!"HS256".equals(header.get("alg"))) return store(cacheKey, ValidationResult.fail("Unsupported alg: " + header.get("alg")));
        String expected = hmac(parts[0] + "." + parts[1], secret);
        if (!constantTimeEq(expected, parts[2])) return store(cacheKey, ValidationResult.fail("Signature invalid"));
        Map<String, String> claims = decodeJson(parts[1]);
        long exp = parseLong(claims.get("exp"), 0);
        if (exp > 0 && Instant.now().getEpochSecond() > exp) return store(cacheKey, ValidationResult.fail("Token expired"));
        String sub = claims.get("sub");
        if (sub == null || sub.isBlank()) return store(cacheKey, ValidationResult.fail("Missing sub claim"));
        if (requiredRole != null && !requiredRole.isBlank()) {
            String roles = claims.getOrDefault("roles", "");
            if (!roles.toUpperCase().contains(requiredRole.toUpperCase()))
                return store(cacheKey, ValidationResult.fail("Role '" + requiredRole + "' required"));
        }
        return store(cacheKey, ValidationResult.success(claims));
    }

    private Map<String, String> decodeJson(String b64) {
        try {
            String pad = b64 + "==".substring(0, (4 - b64.length() % 4) % 4);
            String json = new String(Base64.getUrlDecoder().decode(pad), StandardCharsets.UTF_8);
            Map<String, String> m = new LinkedHashMap<>();
            json = json.replaceAll("^\\{|\\}$", "");
            for (String p : json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] kv = p.split(":", 2);
                if (kv.length == 2) m.put(kv[0].replaceAll("[\"\\s]",""), kv[1].replaceAll("^[\"\\s]+|[\"\\s]+$",""));
            }
            return m;
        } catch (Exception e) { return Map.of(); }
    }

    private String hmac(String data, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException("HMAC failed", e); }
    }

    private boolean constantTimeEq(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0; for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i); return r == 0;
    }

    private String hashToken(String token) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(h).substring(0, 32);
        } catch (Exception e) { return token.substring(0, Math.min(32, token.length())); }
    }

    private ValidationResult store(String k, ValidationResult r) { cache.put(k, new CachedResult(r, System.currentTimeMillis())); return r; }
    private long parseLong(String s, long d) { try { return s == null ? d : Long.parseLong(s.trim()); } catch (Exception e) { return d; } }

    public record CachedResult(ValidationResult result, long ts) { boolean expired() { return System.currentTimeMillis() - ts > TTL_MS; } }

    public static final class ValidationResult {
        private final boolean valid; private final String error; private final Map<String, String> claims;
        private ValidationResult(boolean v, String e, Map<String, String> c) { valid = v; error = e; claims = c == null ? Map.of() : Collections.unmodifiableMap(c); }
        static ValidationResult success(Map<String, String> c) { return new ValidationResult(true, null, c); }
        static ValidationResult fail(String e) { return new ValidationResult(false, e, null); }
        public boolean isValid() { return valid; }
        public String getError() { return error; }
        public String getClaim(String k) { return claims.getOrDefault(k, ""); }
        public String getClaim(String k, String d) { return claims.getOrDefault(k, d); }
    }
}
