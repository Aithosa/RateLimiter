package com.demo.ratelimiter.common.limiter.permitlimiter;

import com.demo.ratelimiter.common.limiter.Limiter;
import com.demo.ratelimiter.redis.key.common.PermitBucketKey;
import com.demo.ratelimiter.redis.service.RedisService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.*;

/**
 * 令牌桶限流器，以令牌桶为基础
 */
@Slf4j
@Data
public class RateLimiter implements Limiter {
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

    /**
     * 构造函数, 读取配置数据
     */
    public RateLimiter(RateLimiterConfig config) {
        this.name = config.getName();
        this.permitsPerSecond = (config.getPermitsPerSecond() == 0L) ? 1000L : config.getPermitsPerSecond();
        this.maxPermits = config.getMaxPermits();
        this.lock = config.getLock();
        this.redisService = config.getRedisService();
    }

    /**
     * 尝试获取锁
     *
     * @return 获取成功返回 true
     */
    private boolean lock() {
        try {
            // 等待 100 秒，获得锁 100 秒后自动解锁
            return lock.tryLock(100, 100, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 释放锁
     */
    private void unlock() {
        lock.unlock();
    }

    /**
     * 生成并存储默认令牌桶
     * 必须在 lock 内调用
     *
     * @return 返回令牌桶
     */
    public PermitBucket putDefaultBucket() {
        if (!redisService.exists(PermitBucketKey.permitBucket, this.name)) {
            long intervalMicros = TimeUnit.SECONDS.toMicros(1) / permitsPerSecond;
            long nextFreeTicketMicros = MILLISECONDS.toMicros(System.currentTimeMillis());
            PermitBucket permitBucket = new PermitBucket(name, maxPermits, 1, intervalMicros, nextFreeTicketMicros);
            // 存入缓存，设置有效时间
            redisService.setwe(PermitBucketKey.permitBucket, this.name, permitBucket, PermitBucketKey.permitBucket.expireSeconds());
        }
        return redisService.get(PermitBucketKey.permitBucket, this.name, PermitBucket.class);
    }

    /**
     * 获取令牌桶, 并刷新令牌桶状态
     *
     * @return 缓存中的令牌桶或者默认的令牌桶
     */
    public PermitBucket getBucket() {
        // 从缓存中获取桶
        PermitBucket permitBucket = redisService.get(PermitBucketKey.permitBucket, this.name, PermitBucket.class);
        // 如果缓存中没有，进入 putDefaultBucket 中初始化
        if (permitBucket == null) {
            return putDefaultBucket();
        }
//        permitBucket.reSync(stopwatch.readNanos());
//        return redisService.get(PermitBucketKey.permitBucket, this.name, PermitBucket.class);
        return permitBucket;
    }

    /**
     * 更新令牌桶
     *
     * @param permitBucket 新的令牌桶
     */
    private void setBucket(PermitBucket permitBucket) {
        redisService.setwe(PermitBucketKey.permitBucket, this.name, permitBucket, PermitBucketKey.permitBucket.expireSeconds());
    }

    public double getRate() {
        return permitsPerSecond;
    }

    private long reserve(int permits) {
        checkPermits(permits);
        while (true) {
            if (lock()) {
                try {
                    return reserveAndGetWaitLength(permits, MILLISECONDS.toMicros(System.currentTimeMillis()));
                } finally {
                    unlock();
                }
            }
        }
    }

    private long reserveAndGetWaitLength(long permits, long nowMicros) {
        long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
        return max(momentAvailable - nowMicros, 0);
    }

    private long reserveEarliestAvailable(long requiredPermits, long nowMicros) {
        PermitBucket bucket = getBucket();
        bucket.reSync(nowMicros);

        // 结合这次请求，当前总共能提供出去的令牌数
        long storedPermitsToSpend = min(requiredPermits, bucket.getStoredPermits());
        // 这次请求还欠的令牌数
        long freshPermits = requiredPermits - storedPermitsToSpend;
        // 生成还欠的令牌数需要花的时间
        long waitMicros = freshPermits * bucket.getIntervalMicros();

        bucket.setNextFreeTicketMicros(saturatedAdd(bucket.getNextFreeTicketMicros(), waitMicros));
        long returnValue = bucket.getNextFreeTicketMicros();
        // 这里不为负，最多为0，后面会休眠负令牌清零的时间，等待令牌恢复
        bucket.setStoredPermits(bucket.getStoredPermits() - storedPermitsToSpend);
        setBucket(bucket);

        return returnValue;
    }

    private long estimateEarliestAvailable(long requiredPermits, long nowMicros) {
        PermitBucket bucket = getBucket();
        bucket.reSync(nowMicros);
//        long returnValue = bucket.getNextFreeTicketMicros();
        // 结合这次请求，当前总共能提供出去的令牌数
        long storedPermitsToSpend = min(requiredPermits, bucket.getStoredPermits());
        // 这次请求还欠的令牌数
        long freshPermits = requiredPermits - storedPermitsToSpend;
        // 生成还欠的令牌数需要花的时间
        long waitMicros = freshPermits * bucket.getIntervalMicros();

        return saturatedAdd(bucket.getNextFreeTicketMicros(), waitMicros);
    }

    /**
     * 获取一个令牌
     *
     * @return 成功返回 true
     */
    @Override
    public double acquire() {
        return acquire(1);
    }

    /**
     * 尝试获取 permits 个令牌
     *
     * @return 获取成功返回 true，失败返回 false
     */
    public double acquire(int permits) {
        long microsToWait = reserve(permits);
        sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / SECONDS.toMicros(1L);
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 以毫秒为单位
     */
    public boolean tryAcquire(long timeout) {
        return tryAcquire(1, timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取成功或超时才返回
     *
     * @param permits 获取的令牌数
     * @param timeout 超时时间，单位为秒
     */
    public boolean tryAcquire(long permits, long timeout, TimeUnit unit) {
        checkPermits(permits);
        long timeoutMicros = max(unit.toMicros(timeout), 0);
        long waitMicros = 0L;
        while (true) {
            if (lock()) {
                try {
                    long nowMicros = unit.toMicros(System.currentTimeMillis());
                    if (!canAcquire(permits, nowMicros, timeoutMicros)) {
                        return false;
                    } else {
                        waitMicros = reserveAndGetWaitLength(permits, nowMicros);
                    }
                } finally {
                    unlock();
                }
                sleepMicrosUninterruptibly(waitMicros);
                return true;
            }
        }
    }

    /**
     * 添加指定数量令牌, 不能超过桶的大小
     *
     * @param permits 要添加的令牌数
     */
    public void addPermits(long permits) {
        checkPermits(permits);

        while (true) {
            if (lock()) {
                try {
                    PermitBucket bucket = getBucket();
                    long now = System.nanoTime();
                    bucket.reSync(now);
                    long newPermits = calculateAddPermits(bucket, permits);
                    bucket.setStoredPermits(newPermits);
                    setBucket(bucket);
                    return;
                } finally {
                    unlock();
                }
            }
        }
    }

    /**
     * 计算添加之后桶里的令牌数
     *
     * @param bucket     桶
     * @param addPermits 添加的令牌数
     * @return
     */
    private long calculateAddPermits(PermitBucket bucket, long addPermits) {
        long newPermits = bucket.getStoredPermits() + addPermits;

        if (newPermits > bucket.getMaxPermits()) {
            newPermits = bucket.getMaxPermits();
        }
        return newPermits;
    }

    /**
     * 当前是否可以获取到令牌，如果获取不到，至少需要等多久
     *
     * @param permits 请求的令牌数
     * @return 等待时间，单位是纳秒。为 0 表示可以马上获取
     */
    private long canAcquire(long permits) {
        // 读取redis中的令牌数据, 同步令牌状态, 写回redis
        PermitBucket bucket = getBucket();
        long now = System.nanoTime();
        bucket.reSync(now);
        setBucket(bucket);

        if (permits <= bucket.getStoredPermits()) {
            return 0L;
        } else {
            return (permits - bucket.getStoredPermits()) * bucket.getIntervalMicros();
        }
    }

    private boolean canAcquire(long permits, long nowMicros, long timeoutMicros) {
//        System.out.println("下一个请求可以处理的时间: " + queryEarliestAvailable(permits, nowMicros));
        return queryEarliestAvailable(permits, nowMicros) - timeoutMicros <= nowMicros;
    }

    private long queryEarliestAvailable(long permits, long nowMicros) {
        return estimateEarliestAvailable(permits, nowMicros);
    }

    private boolean acquireInTime(long startNanos, long waitNanos, long timeoutNanos) {
//        return waitNanos - timeoutNanos <= startNanos;
        return waitNanos <= timeoutNanos;
    }

    /**
     * 校验 token 值
     *
     * @param permits token 值
     */
    private void checkPermits(long permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("Request/Put permits " + permits + " must be positive");
        }
    }

    public static void sleepMicrosUninterruptibly(long micros) {
        if (micros > 0) {
            sleepUninterruptibly(micros, MICROSECONDS);
        }
    }

    public static void sleepUninterruptibly(long sleepFor, TimeUnit unit) {
        boolean interrupted = false;
        try {
            long remainingNanos = unit.toNanos(sleepFor);
            long end = System.nanoTime() + remainingNanos;
            while (true) {
                try {
                    // TimeUnit.sleep() treats negative timeouts just like zero.
                    NANOSECONDS.sleep(remainingNanos);
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                    remainingNanos = end - System.nanoTime();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 暂时使用Guava的@beta(是否有替代函数)
     */
    public static long saturatedAdd(long a, long b) {
        long naiveSum = a + b;
        if ((a ^ b) < 0 | (a ^ naiveSum) >= 0) {
            // If a and b have different signs or a has the same sign as the result then there was no
            // overflow, return.
            return naiveSum;
        }
        // we did over/under flow, if the sign is negative we should return MAX otherwise MIN
        return Long.MAX_VALUE + ((naiveSum >>> (Long.SIZE - 1)) ^ 1);
    }
}
