package com.demo.ratelimiter.common.limiter.permitlimiter;

import com.demo.ratelimiter.common.Factory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterFactory implements Factory {
    private static final Map<String, RateLimiter> RATELIMITERS = new ConcurrentHashMap<>();
    private static final Map<RateLimiter, String> RATELIMITERS_NAME = new ConcurrentHashMap<>();

    public RateLimiter getPermitLimiter(RateLimiterConfig config) {
        RateLimiter rateLimiter = RATELIMITERS.get(config.getName());
        if (rateLimiter == null) {
            rateLimiter = new RateLimiter(config);
            String name = config.getName();
            RATELIMITERS.putIfAbsent(name, rateLimiter);
            RATELIMITERS_NAME.putIfAbsent(rateLimiter, name);
            rateLimiter = RATELIMITERS.get(name);
            rateLimiter.putDefaultBucket();
        }
        return rateLimiter;
    }

    @Override
    public void destroy(Object obj) {
        if (obj instanceof RateLimiter) {
            String name = RATELIMITERS_NAME.remove(obj);
            RATELIMITERS.remove(name);
        }
    }
}
