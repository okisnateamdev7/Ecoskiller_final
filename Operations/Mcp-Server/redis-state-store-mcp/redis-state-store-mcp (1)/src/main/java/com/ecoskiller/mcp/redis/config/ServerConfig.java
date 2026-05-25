package com.ecoskiller.mcp.redis.config;

import java.util.logging.Logger;

/**
 * Loads all server configuration from environment variables.
 *
 * Environment variables (all required in production):
 *   REDIS_HOST          — Redis hostname or IP (default: 127.0.0.1)
 *   REDIS_PORT          — Redis port (default: 6379)
 *   REDIS_PASSWORD      — Redis AUTH password (required in prod)
 *   REDIS_TLS_ENABLED   — "true" to enable TLS (default: false)
 *   REDIS_TLS_CERT_PATH — Path to client TLS certificate
 *   REDIS_TLS_KEY_PATH  — Path to client TLS private key
 *   REDIS_TLS_CA_PATH   — Path to CA certificate for server verification
 *   REDIS_DB            — Redis database number (default: 0)
 *   REDIS_MAX_RETRIES   — Connection retry attempts (default: 3)
 *   REDIS_TIMEOUT_MS    — Command timeout ms (default: 2000)
 *   AUDIT_LOG_PATH      — Audit log file path (default: /var/log/redis-mcp-audit.log)
 *   ENV                 — Environment: dev|test|stage|prod (default: dev)
 */
public class ServerConfig {

    private static final Logger LOGGER = Logger.getLogger(ServerConfig.class.getName());

    // Redis connection
    public static String  REDIS_HOST        = "127.0.0.1";
    public static int     REDIS_PORT        = 6379;
    public static String  REDIS_PASSWORD    = null;
    public static boolean TLS_ENABLED       = false;
    public static String  TLS_CERT_PATH     = null;
    public static String  TLS_KEY_PATH      = null;
    public static String  TLS_CA_PATH       = null;
    public static int     REDIS_DB          = 0;
    public static int     MAX_RETRIES       = 3;
    public static int     TIMEOUT_MS        = 2000;

    // Operational
    public static String  AUDIT_LOG_PATH    = "/var/log/redis-mcp-audit.log";
    public static String  ENV               = "dev";

    public static void loadFromEnv() {
        REDIS_HOST     = getEnv("REDIS_HOST",        REDIS_HOST);
        REDIS_PORT     = getEnvInt("REDIS_PORT",     REDIS_PORT);
        REDIS_PASSWORD = System.getenv("REDIS_PASSWORD");   // never has a default
        TLS_ENABLED    = "true".equalsIgnoreCase(getEnv("REDIS_TLS_ENABLED", "false"));
        TLS_CERT_PATH  = System.getenv("REDIS_TLS_CERT_PATH");
        TLS_KEY_PATH   = System.getenv("REDIS_TLS_KEY_PATH");
        TLS_CA_PATH    = System.getenv("REDIS_TLS_CA_PATH");
        REDIS_DB       = getEnvInt("REDIS_DB",       REDIS_DB);
        MAX_RETRIES    = getEnvInt("REDIS_MAX_RETRIES", MAX_RETRIES);
        TIMEOUT_MS     = getEnvInt("REDIS_TIMEOUT_MS",  TIMEOUT_MS);
        AUDIT_LOG_PATH = getEnv("AUDIT_LOG_PATH",    AUDIT_LOG_PATH);
        ENV            = getEnv("ENV",               ENV);

        validate();

        LOGGER.info("[Config] ENV=" + ENV
                + " REDIS=" + REDIS_HOST + ":" + REDIS_PORT
                + " TLS=" + TLS_ENABLED
                + " DB=" + REDIS_DB);
    }

    private static void validate() {
        if ("prod".equals(ENV) || "stage".equals(ENV)) {
            if (REDIS_PASSWORD == null || REDIS_PASSWORD.isBlank()) {
                throw new IllegalStateException(
                    "[Config] REDIS_PASSWORD is required in " + ENV + " environment");
            }
            if (!TLS_ENABLED) {
                LOGGER.warning("[Config] WARNING: TLS is disabled in " + ENV
                        + " — strongly recommended to enable REDIS_TLS_ENABLED=true");
            }
        }
    }

    private static String getEnv(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }

    private static int getEnvInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            try { return Integer.parseInt(v.trim()); }
            catch (NumberFormatException e) {
                LOGGER.warning("[Config] Invalid integer for " + key + "=" + v + ", using default " + fallback);
            }
        }
        return fallback;
    }
}
