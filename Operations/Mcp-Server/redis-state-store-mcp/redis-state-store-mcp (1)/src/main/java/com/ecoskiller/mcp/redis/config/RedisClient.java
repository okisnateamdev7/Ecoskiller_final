package com.ecoskiller.mcp.redis.config;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.logging.Logger;

/**
 * Lightweight Redis client for the MCP server.
 *
 * Supports:
 *   - AUTH password authentication
 *   - TLS / mutual TLS (client cert + CA verification)
 *   - SELECT database
 *   - Automatic reconnect with configurable retries
 *   - Full Redis command set needed by all 18 tools
 *
 * All network I/O is synchronous (MCP is synchronous too).
 * Thread-safety: this client is NOT thread-safe; MCP stdio is single-threaded.
 */
public class RedisClient {

    private static final Logger LOGGER = Logger.getLogger(RedisClient.class.getName());

    private Socket         socket;
    private BufferedReader reader;
    private OutputStream   writer;

    private final String  host;
    private final int     port;
    private final String  password;
    private final boolean tlsEnabled;
    private final String  tlsCertPath;
    private final String  tlsKeyPath;
    private final String  tlsCaPath;
    private final int     db;
    private final int     maxRetries;
    private final int     timeoutMs;

    public RedisClient() {
        this.host        = ServerConfig.REDIS_HOST;
        this.port        = ServerConfig.REDIS_PORT;
        this.password    = ServerConfig.REDIS_PASSWORD;
        this.tlsEnabled  = ServerConfig.TLS_ENABLED;
        this.tlsCertPath = ServerConfig.TLS_CERT_PATH;
        this.tlsKeyPath  = ServerConfig.TLS_KEY_PATH;
        this.tlsCaPath   = ServerConfig.TLS_CA_PATH;
        this.db          = ServerConfig.REDIS_DB;
        this.maxRetries  = ServerConfig.MAX_RETRIES;
        this.timeoutMs   = ServerConfig.TIMEOUT_MS;
    }

    // ─── Connection ──────────────────────────────────────────────────────────────

    public void connect() throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                openSocket();
                if (password != null && !password.isBlank()) {
                    String auth = sendCommand("AUTH", password);
                    if (!auth.startsWith("+OK")) {
                        throw new SecurityException("Redis AUTH failed — check REDIS_PASSWORD");
                    }
                }
                if (db != 0) {
                    sendCommand("SELECT", String.valueOf(db));
                }
                LOGGER.info("[Redis] Connected to " + host + ":" + port
                        + " TLS=" + tlsEnabled + " DB=" + db);
                return;
            } catch (Exception e) {
                last = e;
                LOGGER.warning("[Redis] Connect attempt " + attempt + "/" + maxRetries + " failed: " + e.getMessage());
                closeQuietly();
                if (attempt < maxRetries) sleep(500L * attempt);
            }
        }
        throw new IOException("[Redis] Could not connect after " + maxRetries + " attempts", last);
    }

    private void openSocket() throws Exception {
        if (tlsEnabled) {
            SSLContext ctx = buildSslContext();
            SSLSocket  ssl = (SSLSocket) ctx.getSocketFactory().createSocket(host, port);
            ssl.setSoTimeout(timeoutMs);
            ssl.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            ssl.setEnabledCipherSuites(ssl.getSupportedCipherSuites());
            ssl.startHandshake();
            socket = ssl;
        } else {
            socket = new Socket(host, port);
            socket.setSoTimeout(timeoutMs);
        }
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = socket.getOutputStream();
    }

    private SSLContext buildSslContext() throws Exception {
        SSLContext ctx;
        if (tlsCaPath != null && tlsCertPath != null && tlsKeyPath != null) {
            // Mutual TLS
            KeyManagerFactory   kmf = loadClientKeyMaterial();
            TrustManagerFactory tmf = loadCaTrustMaterial();
            ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } else if (tlsCaPath != null) {
            // Server-cert verification only
            TrustManagerFactory tmf = loadCaTrustMaterial();
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
        } else {
            // Default JVM trust store (suitable for publicly-signed certs)
            ctx = SSLContext.getDefault();
        }
        return ctx;
    }

    private KeyManagerFactory loadClientKeyMaterial() throws Exception {
        // Expects PKCS12 keystore at tlsCertPath; override for PEM if needed
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(tlsCertPath)) {
            ks.load(is, "".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "".toCharArray());
        return kmf;
    }

    private TrustManagerFactory loadCaTrustMaterial() throws Exception {
        KeyStore ts = KeyStore.getInstance("JKS");
        try (InputStream is = new FileInputStream(tlsCaPath)) {
            ts.load(is, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        return tmf;
    }

    // ─── RESP Protocol ───────────────────────────────────────────────────────────

    /**
     * Send a Redis command using RESP (inline array) and return the raw first response line.
     */
    public String sendCommand(String... cmdArgs) throws Exception {
        ensureConnected();
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(cmdArgs.length).append("\r\n");
        for (String arg : cmdArgs) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            sb.append("$").append(bytes.length).append("\r\n").append(arg).append("\r\n");
        }
        writer.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        writer.flush();
        return readResponse();
    }

    /** Read a full RESP response from the socket. */
    private String readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Connection closed by Redis");

        char type = line.charAt(0);
        return switch (type) {
            case '+' -> line;                          // Simple string
            case '-' -> throw new RuntimeException("Redis error: " + line.substring(1));
            case ':' -> line;                          // Integer
            case '$' -> {
                int len = Integer.parseInt(line.substring(1));
                if (len == -1) yield "$-1";            // Null bulk string
                char[] buf = new char[len];
                int read = 0;
                while (read < len) read += reader.read(buf, read, len - read);
                reader.readLine();                     // consume trailing CRLF
                yield new String(buf);
            }
            case '*' -> {
                int count = Integer.parseInt(line.substring(1));
                if (count == -1) yield "*-1";
                StringBuilder arr = new StringBuilder("[");
                for (int i = 0; i < count; i++) {
                    if (i > 0) arr.append(",");
                    arr.append(readResponse());
                }
                arr.append("]");
                yield arr.toString();
            }
            default -> line;
        };
    }

    // ─── High-Level Redis Commands ───────────────────────────────────────────────

    public String get(String key) throws Exception {
        String r = sendCommand("GET", key);
        return r.equals("$-1") ? null : r;
    }

    public String set(String key, String value) throws Exception {
        return sendCommand("SET", key, value);
    }

    public String setex(String key, int ttlSeconds, String value) throws Exception {
        return sendCommand("SETEX", key, String.valueOf(ttlSeconds), value);
    }

    /** SET key value NX PX {ttlMs} — atomic distributed lock */
    public String setNxPx(String key, String value, long ttlMs) throws Exception {
        return sendCommand("SET", key, value, "NX", "PX", String.valueOf(ttlMs));
    }

    public String del(String key) throws Exception {
        return sendCommand("DEL", key);
    }

    public String incr(String key) throws Exception {
        return sendCommand("INCR", key);
    }

    public String expire(String key, int ttlSeconds) throws Exception {
        return sendCommand("EXPIRE", key, String.valueOf(ttlSeconds));
    }

    public String ttl(String key) throws Exception {
        return sendCommand("TTL", key);
    }

    public String pttl(String key) throws Exception {
        return sendCommand("PTTL", key);
    }

    public String lpush(String key, String value) throws Exception {
        return sendCommand("LPUSH", key, value);
    }

    public String rpop(String key) throws Exception {
        String r = sendCommand("RPOP", key);
        return r.equals("$-1") ? null : r;
    }

    public String llen(String key) throws Exception {
        return sendCommand("LLEN", key);
    }

    public String lrange(String key, int start, int end) throws Exception {
        return sendCommand("LRANGE", key, String.valueOf(start), String.valueOf(end));
    }

    public String sadd(String key, String member) throws Exception {
        return sendCommand("SADD", key, member);
    }

    public String srem(String key, String member) throws Exception {
        return sendCommand("SREM", key, member);
    }

    public String smembers(String key) throws Exception {
        return sendCommand("SMEMBERS", key);
    }

    public String zadd(String key, double score, String member) throws Exception {
        return sendCommand("ZADD", key, String.valueOf(score), member);
    }

    public String zrank(String key, String member) throws Exception {
        return sendCommand("ZRANK", key, member);
    }

    public String zrange(String key, int start, int end, boolean withScores) throws Exception {
        if (withScores) return sendCommand("ZRANGE", key, String.valueOf(start), String.valueOf(end), "WITHSCORES");
        return sendCommand("ZRANGE", key, String.valueOf(start), String.valueOf(end));
    }

    public String zrevrank(String key, String member) throws Exception {
        return sendCommand("ZREVRANK", key, member);
    }

    public String zscore(String key, String member) throws Exception {
        return sendCommand("ZSCORE", key, member);
    }

    public String publish(String channel, String message) throws Exception {
        return sendCommand("PUBLISH", channel, message);
    }

    public String ping() throws Exception {
        return sendCommand("PING");
    }

    public String info(String section) throws Exception {
        if (section == null) return sendCommand("INFO");
        return sendCommand("INFO", section);
    }

    public String dbsize() throws Exception {
        return sendCommand("DBSIZE");
    }

    public String configGet(String param) throws Exception {
        return sendCommand("CONFIG", "GET", param);
    }

    public String lastsave() throws Exception {
        return sendCommand("LASTSAVE");
    }

    public String bgsave() throws Exception {
        return sendCommand("BGSAVE");
    }

    public String keys(String pattern) throws Exception {
        return sendCommand("KEYS", pattern);
    }

    public String type(String key) throws Exception {
        return sendCommand("TYPE", key);
    }

    public String exists(String key) throws Exception {
        return sendCommand("EXISTS", key);
    }

    // ─── Internals ───────────────────────────────────────────────────────────────

    private void ensureConnected() throws Exception {
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            LOGGER.warning("[Redis] Socket lost, reconnecting...");
            connect();
        }
    }

    private void closeQuietly() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null; reader = null; writer = null;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    public void close() { closeQuietly(); }
}
