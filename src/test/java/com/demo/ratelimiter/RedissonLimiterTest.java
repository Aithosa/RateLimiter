package com.demo.ratelimiter;

import com.demo.ratelimiter.common.redis.service.RedissonService;
import org.junit.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

public class RedissonLimiterTest extends RateLimiterFactoryTest {
    @Autowired
    private RedissonService redissonService;

    /**
     * 测试acquire方法，预期和guava相同
     */
    @Test
    public void redissonLimiterAcquireTest() {
        // 1、 声明一个限流器
        RRateLimiter rateLimiter = redissonService.getRateLimiter("testRedissonLimiterAcquire");
        // 2、 设置速率，1秒中产生2个令牌
        rateLimiter.trySetRate(RateType.OVERALL, 1, 500L, RateIntervalUnit.MILLISECONDS);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; ++i) {
            rateLimiter.acquire(1);
            System.out.println(System.currentTimeMillis() - start);
        }
    }

    /**
     * 测试tryAcquire方法，结果和guava不太相符，如超时时间的作用
     */
    @Test
    public void redissonLimiterTryAcquireTest() {
        // 1、 声明一个限流器
        RRateLimiter rateLimiter = redissonService.getRateLimiter("testRedissonLimiterTryAcquire");
        // 2、 设置速率，1秒中产生2个令牌
        rateLimiter.trySetRate(RateType.OVERALL, 1, 200L, RateIntervalUnit.MILLISECONDS);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 10; ++i) {
            System.out.println("job " + i + ": " + rateLimiter.tryAcquire(200, TimeUnit.MILLISECONDS) + " "
                    + (System.currentTimeMillis() - start));
        }
    }
}
