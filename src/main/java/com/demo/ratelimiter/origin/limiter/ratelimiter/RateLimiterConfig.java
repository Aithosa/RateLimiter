package com.demo.ratelimiter.origin.limiter.ratelimiter;

import com.demo.ratelimiter.common.Constants;
import com.demo.ratelimiter.common.redis.service.RedisService;
import lombok.Getter;
import lombok.ToString;
import org.redisson.api.RLock;

/**
 * 令牌桶配置
 */
@Getter
@ToString
public class RateLimiterConfig {
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
     * 缓存比例
     */
    private final float cache;

    /**
     * 分布式互斥锁
     */
    private final RLock lock;

    /**
     * 用于对Redis进行读取和查找操作
     */
    private final RedisService redisService;

    public RateLimiterConfig(String name, RLock lock, RedisService redisService) {
        this(name, Constants.PERMITS_PER_SECOND, Constants.MAX_PERMITS, 0F, lock, redisService);
    }

    public RateLimiterConfig(String name, long permitsPerSecond, RLock lock, RedisService redisService) {
        this(name, permitsPerSecond, permitsPerSecond, 0F, lock, redisService);
    }

    public RateLimiterConfig(String name, long permitsPerSecond, float cache, RLock lock, RedisService redisService) {
        this(name, permitsPerSecond, permitsPerSecond, cache, lock, redisService);
    }

    public RateLimiterConfig(String name, long permitsPerSecond, long maxPermits, float cache, RLock lock, RedisService redisService) {
        this.name = name;
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = maxPermits;
        this.cache = cache;
        this.lock = lock;
        this.redisService = redisService;
    }
}
