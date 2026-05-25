package com.ecoskiller.mcp.redis.tools;

import com.ecoskiller.mcp.redis.config.RedisConfig;
import com.ecoskiller.mcp.redis.security.AuditLogger;

import java.util.*;

/**
 * Central registry for all 21 Redis MCP tools.
 *
 * Tool categories:
 *   Cache Operations    (5) : cache_get, cache_set, cache_delete, cache_mget, cache_flush_namespace
 *   Session Management  (4) : session_create, session_get, session_refresh, session_destroy
 *   Leaderboard & Ranks (3) : leaderboard_add, leaderboard_get, leaderboard_rank
 *   Rate Limiting       (2) : rate_limit_check, rate_limit_reset
 *   Counters            (3) : counter_increment, counter_get, counter_reset
 *   Pub/Sub             (1) : pubsub_publish
 *   Distributed Locks   (2) : lock_acquire, lock_release
 *   Monitoring          (1) : redis_health
 *                    ─────
 *   Total              21 tools
 */
public class ToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public ToolRegistry(RedisConfig cfg, AuditLogger audit) {
        // ── Cache Operations ──────────────────────────────────────────────────
        register(new CacheGetTool(cfg, audit));
        register(new CacheSetTool(cfg, audit));
        register(new CacheDeleteTool(cfg, audit));
        register(new CacheMGetTool(cfg, audit));
        register(new CacheFlushNamespaceTool(cfg, audit));

        // ── Session Management ────────────────────────────────────────────────
        register(new SessionCreateTool(cfg, audit));
        register(new SessionGetTool(cfg, audit));
        register(new SessionRefreshTool(cfg, audit));
        register(new SessionDestroyTool(cfg, audit));

        // ── Leaderboard & Rankings ────────────────────────────────────────────
        register(new LeaderboardAddTool(cfg, audit));
        register(new LeaderboardGetTool(cfg, audit));
        register(new LeaderboardRankTool(cfg, audit));

        // ── Rate Limiting ─────────────────────────────────────────────────────
        register(new RateLimitCheckTool(cfg, audit));
        register(new RateLimitResetTool(cfg, audit));

        // ── Atomic Counters ───────────────────────────────────────────────────
        register(new CounterIncrementTool(cfg, audit));
        register(new CounterGetTool(cfg, audit));
        register(new CounterResetTool(cfg, audit));

        // ── Pub/Sub ───────────────────────────────────────────────────────────
        register(new PubSubPublishTool(cfg, audit));

        // ── Distributed Locks ─────────────────────────────────────────────────
        register(new LockAcquireTool(cfg, audit));
        register(new LockReleaseTool(cfg, audit));

        // ── Monitoring ────────────────────────────────────────────────────────
        register(new RedisHealthTool(cfg, audit));
    }

    private void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public McpTool find(String name) {
        return tools.get(name);
    }

    public Collection<McpTool> allTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public int size() {
        return tools.size();
    }
}
