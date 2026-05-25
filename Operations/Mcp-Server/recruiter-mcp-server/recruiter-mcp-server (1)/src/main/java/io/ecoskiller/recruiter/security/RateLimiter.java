package io.ecoskiller.recruiter.security;

import io.ecoskiller.recruiter.config.ServerConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class BucketState {
    final AtomicInteger sec = new AtomicInteger();
    final AtomicInteger min = new AtomicInteger();
    volatile long secBucket = System.currentTimeMillis() / 1000;
    volatile long minBucket = System.currentTimeMillis() / 60_000;
}

public class RateLimiter {
    private final int maxSec, maxMin;
    private final ConcurrentHashMap<String, BucketState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> banned = new ConcurrentHashMap<>();

    public RateLimiter(ServerConfig c) { this.maxSec = c.getRateLimitPerSec(); this.maxMin = c.getRateLimitPerMin(); }

    public boolean allow(String key) {
        Long exp = banned.get(key);
        if (exp != null) {
            if (exp == 0 || System.currentTimeMillis() < exp) return false;
            banned.remove(key);
        }
        BucketState s = states.computeIfAbsent(key, k -> new BucketState());
        long nowS = System.currentTimeMillis() / 1000;
        long nowM = System.currentTimeMillis() / 60_000;
        if (s.secBucket != nowS) { s.sec.set(0); s.secBucket = nowS; }
        if (s.minBucket != nowM) { s.min.set(0); s.minBucket = nowM; }
        return s.sec.incrementAndGet() <= maxSec && s.min.incrementAndGet() <= maxMin;
    }

    public void ban(String key, int secs) {
        banned.put(key, secs == 0 ? 0L : System.currentTimeMillis() + secs * 1000L);
    }
    public void unban(String key) { banned.remove(key); }
    public boolean isBanned(String key) {
        Long e = banned.get(key);
        if (e == null) return false;
        if (e == 0 || System.currentTimeMillis() < e) return true;
        banned.remove(key); return false;
    }
}
