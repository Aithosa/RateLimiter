package com.demo.ratelimiter.redis.service;

import org.redisson.api.RLock;
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
}
