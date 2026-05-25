package com.ecoskiller.mcp.redis.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.time.Duration;

/**
 * Redis connection configuration.
 *
 * All values sourced from environment variables — no hardcoded secrets.
 *
 * Environment variables:
 *   REDIS_HOST          — Redis host (default: localhost)
 *   REDIS_PORT          — Redis port (default: 6379)
 *   REDIS_PASSWORD      — Redis AUTH password (required in production)
 *   REDIS_TLS_ENABLED   — "true" to enforce TLS (required in production)
 *   REDIS_TLS_CERT_PATH — Path to truststore JKS (optional, for self-signed certs)
 *   REDIS_DB            — Database index (default: 0)
 *   REDIS_MAX_POOL      — Max connection pool size (default: 20)
 *   REDIS_TIMEOUT_MS    — Socket timeout ms (default: 3000)
 *   REDIS_KEY_PREFIX    — Global key namespace prefix (default: ecoskiller)
 *   MCP_API_KEY         — Secret key required in every tool call
 *   MCP_API_KEY_2       — Optional secondary API key (key rotation support)
 */
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    // Connection
    public final String  host;
    public final int     port;
    public final String  password;
    public final boolean tlsEnabled;
    public final String  tlsCertPath;
    public final int     db;

    // Pool
    public final int maxPoolSize;
    public final int timeoutMs;

    // Security
    public final String keyPrefix;
    public final String[] validApiKeys;

    private JedisPool pool;

    private RedisConfig(String host, int port, String password, boolean tlsEnabled,
                        String tlsCertPath, int db, int maxPoolSize, int timeoutMs,
                        String keyPrefix, String[] validApiKeys) {
        this.host        = host;
        this.port        = port;
        this.password    = password;
        this.tlsEnabled  = tlsEnabled;
        this.tlsCertPath = tlsCertPath;
        this.db          = db;
        this.maxPoolSize = maxPoolSize;
        this.timeoutMs   = timeoutMs;
        this.keyPrefix   = keyPrefix;
        this.validApiKeys = validApiKeys;
    }

    /** Build configuration from environment variables (12-factor app pattern). */
    public static RedisConfig fromEnvironment() {
        String host       = env("REDIS_HOST",        "localhost");
        int    port       = Integer.parseInt(env("REDIS_PORT", "6379"));
        String password   = env("REDIS_PASSWORD",    "");
        boolean tls       = "true".equalsIgnoreCase(env("REDIS_TLS_ENABLED", "false"));
        String certPath   = env("REDIS_TLS_CERT_PATH", "");
        int    db         = Integer.parseInt(env("REDIS_DB", "0"));
        int    pool       = Integer.parseInt(env("REDIS_MAX_POOL", "20"));
        int    timeout    = Integer.parseInt(env("REDIS_TIMEOUT_MS", "3000"));
        String prefix     = env("REDIS_KEY_PREFIX",  "ecoskiller");

        // Collect valid API keys (supports key rotation via two env vars)
        String k1 = env("MCP_API_KEY",   "");
        String k2 = env("MCP_API_KEY_2", "");
        String[] keys = java.util.Arrays.stream(new String[]{k1, k2})
                                        .filter(k -> !k.isEmpty())
                                        .toArray(String[]::new);

        if (keys.length == 0) {
            log.warn("WARNING: MCP_API_KEY not set — all requests will be rejected");
        }

        // Production safety checks
        if (!tls && !"localhost".equals(host) && !"127.0.0.1".equals(host)) {
            log.warn("SECURITY WARNING: TLS is disabled for non-localhost Redis host '{}'", host);
        }
        if (password.isEmpty() && !"localhost".equals(host)) {
            log.warn("SECURITY WARNING: REDIS_PASSWORD not set for non-localhost host");
        }

        log.info("Redis config: host={}:{}, tls={}, db={}, pool={}, prefix={}",
                 host, port, tls, db, pool, prefix);

        return new RedisConfig(host, port, password, tls, certPath, db, pool, timeout, prefix, keys);
    }

    // -------------------------------------------------------------------------
    // Connection pool
    // -------------------------------------------------------------------------

    /** Lazily initialise and return the shared Jedis connection pool. */
    public synchronized JedisPool getPool() {
        if (pool != null) return pool;

        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxTotal(maxPoolSize);
        poolCfg.setMaxIdle(maxPoolSize / 2);
        poolCfg.setMinIdle(2);
        poolCfg.setMaxWait(Duration.ofMillis(timeoutMs));
        poolCfg.setTestOnBorrow(true);
        poolCfg.setTestWhileIdle(true);
        poolCfg.setEvictionPolicy(new org.apache.commons.pool2.impl.DefaultEvictionPolicy<>());

        if (tlsEnabled) {
            pool = buildTlsPool(poolCfg);
        } else {
            String pwd = password.isEmpty() ? null : password;
            pool = new JedisPool(poolCfg, host, port, timeoutMs, pwd, db);
        }

        log.info("Jedis connection pool initialised (maxTotal={})", maxPoolSize);
        return pool;
    }

    private JedisPool buildTlsPool(JedisPoolConfig poolCfg) {
        try {
            SSLContext sslContext;
            if (!tlsCertPath.isEmpty()) {
                // Custom truststore for self-signed / internal CA certs
                KeyStore ks = KeyStore.getInstance("JKS");
                try (FileInputStream fis = new FileInputStream(tlsCertPath)) {
                    ks.load(fis, null);
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                sslContext = SSLContext.getInstance("TLSv1.3");
                sslContext.init(null, tmf.getTrustManagers(), null);
            } else {
                // Use the JVM default truststore (GCP MemoryStore / AWS ElastiCache certs)
                sslContext = SSLContext.getDefault();
            }

            String pwd = password.isEmpty() ? null : password;
            return new JedisPool(poolCfg, host, port, timeoutMs, timeoutMs,
                                 pwd, db, null, true,
                                 sslContext.getSocketFactory(), null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build TLS JedisPool: " + e.getMessage(), e);
        }
    }

    /** Get a raw Jedis connection — callers must close() it to return to pool. */
    public Jedis getConnection() {
        return getPool().getResource();
    }

    // -------------------------------------------------------------------------
    // Key namespace helper
    // -------------------------------------------------------------------------

    /**
     * Build a namespaced key: ecoskiller:{namespace}:{key}
     * Prevents cross-namespace key collisions across tools.
     */
    public String ns(String namespace, String key) {
        // Security: reject keys with special characters that could cause injection
        if (key.contains("\n") || key.contains("\r") || key.contains(" ")) {
            throw new IllegalArgumentException("Key contains illegal characters: " + key);
        }
        return keyPrefix + ":" + namespace + ":" + key;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static String env(String name, String defaultValue) {
        String val = System.getProperty(name);
        if (val == null || val.isEmpty()) {
            val = System.getenv(name);
        }
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    public void close() {
        if (pool != null && !pool.isClosed()) {
            pool.close();
            log.info("Redis connection pool closed");
        }
    }
}
