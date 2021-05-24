package com.demo.ratelimiter.common.limiter.permitlimiter;

import com.demo.ratelimiter.common.Constants;
import com.demo.ratelimiter.redis.service.RedisService;
import lombok.Getter;
import lombok.ToString;
import org.redisson.api.RLock;

/**
 * 令牌桶配置
 */
@Getter
@ToString
public class RateLimiterConfig {
    private final RedisService redisService;

    /**
     * 唯一标识
     */
    private final String name;

    /**
     * 每秒存入的令牌数
     */
    private final long permitsPerSecond;

    /**
     * 最大存储令牌数
     */
    private final long maxPermits;

    /**
     * 分布式互斥锁
     */
    private final RLock lock;

    public RateLimiterConfig(String name, RLock lock, RedisService redisService) {
        this(name, Constants.PERMITS_PER_SECOND, Constants.MAX_PERMITS, lock, redisService);
    }

    public RateLimiterConfig(String name, long permitsPerSecond, long maxPermits, RLock lock, RedisService redisService) {
        this.name = name;
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = maxPermits;
        this.lock = lock;
        this.redisService = redisService;
    }

    public RateLimiterConfig(String name, long permitsPerSecond, RLock lock, RedisService redisService) {
        this.name = name;
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = permitsPerSecond;
        this.lock = lock;
        this.redisService = redisService;
    }
}
