package com.demo.ratelimiter.common.redis.service;

import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RedissonService {
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    public RLock getRLock(String key) {
        return redissonClient.getLock(key);
    }

    /**
     * 获取读写锁
     *
     * @param key
     * @return
     */
    public RReadWriteLock getRWLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    /**
     * 获取限流器
     *
     * @param key
     * @return
     */
    public RRateLimiter getRateLimiter(String key) {
        return redissonClient.getRateLimiter(key);
    }
}
