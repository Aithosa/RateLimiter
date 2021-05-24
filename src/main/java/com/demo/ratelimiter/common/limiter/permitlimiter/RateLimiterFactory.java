package com.demo.ratelimiter.common.limiter.permitlimiter;

import com.demo.ratelimiter.common.Factory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterFactory implements Factory {
    private static final Map<String, RateLimiter> PERMITLIMITERS = new ConcurrentHashMap<>();
    private static final Map<RateLimiter, String> PERMITLIMITER_NAME = new ConcurrentHashMap<>();

    public RateLimiter getPermitLimiter(RateLimiterConfig config) {
        RateLimiter rateLimiter = PERMITLIMITERS.get(config.getName());
        if (rateLimiter == null) {
            rateLimiter = new RateLimiter(config);
            String name = config.getName();
            PERMITLIMITERS.putIfAbsent(name, rateLimiter);
            PERMITLIMITER_NAME.putIfAbsent(rateLimiter, name);
            rateLimiter = PERMITLIMITERS.get(name);
            rateLimiter.putDefaultBucket();
        }
        return rateLimiter;
    }

    @Override
    public void destroy(Object obj) {
        if (obj instanceof RateLimiter) {
            String name = PERMITLIMITER_NAME.remove(obj);
            PERMITLIMITERS.remove(name);
        }
    }
}
