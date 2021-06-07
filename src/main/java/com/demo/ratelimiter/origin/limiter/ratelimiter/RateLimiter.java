package com.demo.ratelimiter.origin.limiter.ratelimiter;

import com.demo.ratelimiter.common.Constant;
import com.demo.ratelimiter.common.redis.key.common.PermitBucketKey;
import com.demo.ratelimiter.common.redis.service.RedisService;
import com.demo.ratelimiter.origin.limiter.Limiter;
import lombok.Getter;
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
@Getter
public class RateLimiter implements Limiter {
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
     * 超时时间 - 由缓存队列比例计算
     */
    private final long timeoutMicros;

    /**
     * 分布式互斥锁
     */
    private final RLock lock;

    /**
     * 用于对Redis进行读取和查找操作
     */
    private final RedisService redisService;

    /**
     * 构造函数, 读取配置数据
     */
    public RateLimiter(RateLimiterConfig config) {
        this.name = config.getName();
        this.permitsPerSecond = (config.getPermitsPerSecond() == 0L) ? 1000L : config.getPermitsPerSecond();
        this.maxPermits = config.getMaxPermits();
        long intervalMicros = TimeUnit.SECONDS.toMicros(1) / permitsPerSecond;
        this.timeoutMicros = (long) (config.getCache() * config.getPermitsPerSecond() * intervalMicros);
        this.lock = config.getLock();
        this.redisService = config.getRedisService();
        log.info("Creat rateLimiter: {}, maxPermits: {}, permitsPerSecond: {}, intervalMicros:{}, timeoutMicros: {}",
                name, maxPermits, permitsPerSecond, intervalMicros, timeoutMicros);
    }

    public double getRate() {
        return permitsPerSecond;
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
     * 获取令牌桶, 不刷新，用于acquire
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
        return permitBucket;
    }

    /**
     * 获取令牌桶, 并刷新令牌桶状态, 用于仅查询
     *
     * @return 缓存中的令牌桶或者默认的令牌桶
     */
    public PermitBucket getBucketAndSync() {
        // 从缓存中获取桶
        PermitBucket permitBucket = redisService.get(PermitBucketKey.permitBucket, this.name, PermitBucket.class);
        // 如果缓存中没有，进入 putDefaultBucket 中初始化
        if (permitBucket == null) {
            return putDefaultBucket();
        }
        permitBucket.reSync(MILLISECONDS.toMicros(System.currentTimeMillis()));
        return redisService.get(PermitBucketKey.permitBucket, this.name, PermitBucket.class);
    }

    /**
     * 更新令牌桶
     *
     * @param permitBucket 新的令牌桶
     */
    private void setBucket(PermitBucket permitBucket) {
        redisService.setwe(PermitBucketKey.permitBucket, this.name, permitBucket, PermitBucketKey.permitBucket.expireSeconds());
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
            } else {
                System.out.println("lock failed, try another");
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

        bucket.setNextFreeTicketMicros(Limiter.saturatedAdd(bucket.getNextFreeTicketMicros(), waitMicros));
        long returnValue = bucket.getNextFreeTicketMicros();
        // 这里不为负，最多为0，后面会休眠负令牌清零的时间，等待令牌恢复
        bucket.setStoredPermits(bucket.getStoredPermits() - storedPermitsToSpend);
        setBucket(bucket);

        return returnValue;
    }

    private boolean canAcquire(long permits, long nowMicros, long timeoutMicros) {
        return queryEarliestAvailable(permits, nowMicros) - timeoutMicros <= nowMicros;
    }

    private long queryEarliestAvailable(long permits, long nowMicros) {
        return estimateEarliestAvailable(permits, nowMicros);
    }

    private long estimateEarliestAvailable(long requiredPermits, long nowMicros) {
        PermitBucket bucket = getBucket();
        bucket.reSync(nowMicros);

        // 结合这次请求，当前总共能提供出去的令牌数
        long storedPermitsToSpend = min(requiredPermits, bucket.getStoredPermits());
        // 这次请求还欠的令牌数
        long freshPermits = requiredPermits - storedPermitsToSpend;
        // 生成还欠的令牌数需要花的时间
        long waitMicros = freshPermits * bucket.getIntervalMicros();

        return Limiter.saturatedAdd(bucket.getNextFreeTicketMicros(), waitMicros);
    }

//    private long estimateEarliestAvailable(long requiredPermits, long nowMicros) {
//        PermitBucket bucket = getBucket();
//        bucket.reSync(nowMicros);
//        return bucket.getNextFreeTicketMicros();
//    }

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
        Limiter.sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / SECONDS.toMicros(1L);
    }

    /**
     * 以毫秒为单位, 使用预设值的timeout, 考虑开关状态
     */
    public boolean tryAcquire(String switchConf) {
        if (Constant.OFF.getCode().equals(switchConf)) {
            return true;
        }
        return tryAcquire(1, timeoutMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * 以微秒为单位, 使用预设值的timeout
     */
    public boolean tryAcquire() {
        return tryAcquire(1, timeoutMicros, TimeUnit.MICROSECONDS);
    }

    /**
     * 以毫秒为单位
     */
    public boolean tryAcquire(long timeoutMillis) {
        return tryAcquire(1, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 获取成功或超时才返回
     *
     * @param permits 获取的令牌数
     * @param timeout 超时时间，单位为微秒, 不需要转换
     */
    public boolean tryAcquire(long permits, long timeout, TimeUnit unit) {
        checkPermits(permits);
        long timeoutMicros = max(unit.toMicros(timeout), 0);
        long waitMicros = 0L;
        while (true) {
            if (lock()) {
                try {
                    long nowMicros = MILLISECONDS.toMicros(System.currentTimeMillis());
                    if (!canAcquire(permits, nowMicros, timeoutMicros)) {
                        return false;
                    } else {
                        waitMicros = reserveAndGetWaitLength(permits, nowMicros);
                    }
                } finally {
                    unlock();
                }
                Limiter.sleepMicrosUninterruptibly(waitMicros);
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
                    bucket.reSync(MILLISECONDS.toMicros(System.currentTimeMillis()));
                    long newPermits = calculateAddPermits(bucket, permits);
                    long newNextFreeTicketMicros = calculateNextFreeTicketMicros(bucket, newPermits);
                    bucket.setStoredPermits(newPermits);
                    bucket.setNextFreeTicketMicros(newNextFreeTicketMicros);
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
     * @return 实际添加的令牌数
     */
    private long calculateAddPermits(PermitBucket bucket, long addPermits) {
        long newPermits = bucket.getStoredPermits() + addPermits;

        if (newPermits > bucket.getMaxPermits()) {
            newPermits = bucket.getMaxPermits();
        }
        return newPermits;
    }

    /**
     * 计算新的NextFreeTicketMicros
     *
     * @param bucket     桶
     * @param addPermits 实际添加的令牌数
     * @return 新的NextFreeTicketMicros
     */
    private long calculateNextFreeTicketMicros(PermitBucket bucket, long addPermits) {
        long addTimeMicros = bucket.getIntervalMicros() * addPermits;
        long nowMicros = MILLISECONDS.toMicros(System.currentTimeMillis());
        long newNextFreeTicketMicros = nowMicros + addTimeMicros;

        if (newNextFreeTicketMicros > bucket.getNextFreeTicketMicros()) {
            return nowMicros;
        }
        return newNextFreeTicketMicros;
    }

//    /**
//     * 当前是否可以获取到令牌，如果获取不到，至少需要等多久
//     *
//     * @param permits 请求的令牌数
//     * @return 等待时间，单位是纳秒。为 0 表示可以马上获取
//     */
//    private long canAcquire(long permits) {
//        // 读取redis中的令牌数据, 同步令牌状态, 写回redis
//        PermitBucket bucket = getBucket();
//        long now = System.nanoTime();
//        bucket.reSync(now);
//        setBucket(bucket);
//
//        if (permits <= bucket.getStoredPermits()) {
//            return 0L;
//        } else {
//            return (permits - bucket.getStoredPermits()) * bucket.getIntervalMicros();
//        }
//    }

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
}
