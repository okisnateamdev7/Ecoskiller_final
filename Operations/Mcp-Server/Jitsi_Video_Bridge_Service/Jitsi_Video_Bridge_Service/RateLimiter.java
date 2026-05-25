package io.ecoskiller.mcp.jitsi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter for MCP tool calls.
 *
 * Prevents abuse of sensitive tools (recording control, conference create,
 * bridge scaling) by limiting call frequency per tool name.
 *
 * Default: 60 requests per 60-second sliding window per tool.
 */
public class RateLimiter {

    private final int  maxRequests;
    private final long windowMs;

    private static class Bucket {
        final AtomicInteger count    = new AtomicInteger(0);
        final AtomicLong    windowStart = new AtomicLong(System.currentTimeMillis());
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs    = windowMs;
    }

    /**
     * @param toolName tool identifier
     * @return true if the call is allowed; false if rate limit exceeded
     */
    public boolean allow(String toolName) {
        Bucket bucket = buckets.computeIfAbsent(toolName, k -> new Bucket());
        long now = System.currentTimeMillis();

        // Reset window if expired
        long windowStart = bucket.windowStart.get();
        if (now - windowStart > windowMs) {
            // CAS to avoid race — only one thread resets
            if (bucket.windowStart.compareAndSet(windowStart, now)) {
                bucket.count.set(0);
            }
        }

        return bucket.count.incrementAndGet() <= maxRequests;
    }
}
