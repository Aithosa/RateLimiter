package com.demo.ratelimiter;

import com.demo.ratelimiter.common.limiter.permitlimiter.RateLimiter;
import com.demo.ratelimiter.common.limiter.permitlimiter.RateLimiterConfig;
import com.demo.ratelimiter.common.limiter.permitlimiter.RateLimiterFactory;
import com.demo.ratelimiter.redis.service.RedisService;
import com.demo.ratelimiter.redis.service.RedissonService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.math.LongMath.saturatedAdd;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RateLimiterApplication.class)
public class RateLimiterFactoryTest {
    private static final int JOB_NUMS = 20;

    /**
     * 每秒发送请求限制
     */
    private static final long REQUEST_LIMIT_PER_SECONDS = 5L;

    /**
     * 缓冲区长度
     */
    private static final double CACHE = 0.5;

    private static double CACHE_SIZE = 0L;

    /**
     * 超时时间
     */
    private static Long TIMEOUT = 0L;

    private static final long SLEEP_TIME = 0L;

    /**
     * 统计参数
     */
    private static long START_TIME = System.currentTimeMillis();

    private static int successJob = 0;

    private static int failJob = 0;

    @Autowired
    RedisService redisService;

    @Autowired
    RedissonService redissonService;

    @Test
    public void PermitLimiterTest() throws Exception {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("permitLimiter", REQUEST_LIMIT_PER_SECONDS, redissonService.getRLock("permitLock"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        CACHE_SIZE = (int) (REQUEST_LIMIT_PER_SECONDS * CACHE);
        TIMEOUT = (long) (CACHE_SIZE * (1000L / REQUEST_LIMIT_PER_SECONDS));
//        TIMEOUT = (long) ((CACHE_SIZE + REQUEST_LIMIT_PER_SECONDS) * (1000L / REQUEST_LIMIT_PER_SECONDS));

        statistic(rateLimiter);

        ExecutorService executor = Executors.newFixedThreadPool(50);

        START_TIME = System.currentTimeMillis();
        for (int i = 0; i < JOB_NUMS; ++i) {
            int finalI = i;
            executor.execute(() -> {
                String rsp = acquireJob(finalI, rateLimiter);
            });
        }

        Thread.sleep(2000L);
        jobCountStatistic(JOB_NUMS);

        executor.shutdown();
    }

    /**
     * 通过rateLimiter控制接口调用
     */
    private String acquireJob(int jobNo, RateLimiter rateLimiter) {
        if (!rateLimiter.tryAcquire(TIMEOUT)) {
            failJob++;
            System.out.println("req: " + jobNo + ", rsp is: " + null);
        } else {
            try {
                // 网关出口
                String rsp = job(jobNo);
                System.out.println("req: " + jobNo + ", rsp is: " + rsp);
                return rsp;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 模拟对外部接口的调用
     */
    private static String job(int req) throws Exception {
        int rsp = req * 2 + req;

        jobTimeStatistic(String.valueOf(req));
        Thread.sleep(100L);

        return String.valueOf(rsp);
    }

    private static void jobTimeStatistic(String req) {
        successJob++;
        long totalTime = System.currentTimeMillis() - START_TIME;
        System.out.println("job " + req + " takes " + totalTime + " ms to start.");
    }

    private void jobCountStatistic(int jobNums) {
        System.out.println("\ntotal " + jobNums + " jobs, success " + successJob + " jobs, fail " + failJob + " jobs.");
    }

    private static void statistic(RateLimiter rateLimiter) {
        System.out.println("---------- statistic ----------");
        System.out.println("Request limit per seconds: " + rateLimiter.getRate());
        System.out.println("Cache size: " + CACHE_SIZE);
        System.out.println("Timeout: " + TIMEOUT + " ms");
        System.out.println("Sleep Time: " + SLEEP_TIME + " ms");
        System.out.println("-------------------------------\n");
    }

    @Test
    public void tryAcquireTest() {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("testPermitLimiter2", 1, redissonService.getRLock("testPermitLock2"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        for (int i = 1; i <= 10; i++) {
            if (rateLimiter.tryAcquire(0L)) {
                System.out.println(i + ": success, left permit: " + rateLimiter.getBucket().getStoredPermits());
            } else {
                System.out.println(i + ": fail, left permit: " + rateLimiter.getBucket().getStoredPermits());
                try {
                    Thread.sleep(500L);
                    System.out.println("after sleep, permit: " + rateLimiter.getBucket().getStoredPermits());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println();
        }
    }

    @Test
    public void acquireTest() {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("testPermitLimiter3", 2, redissonService.getRLock("testPermitLock3"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        for (int i = 1; i <= 5; i++) {
            System.out.println("acquire time: " + rateLimiter.acquire());
        }
    }

    @Test
    public void timeTest() {
        long remainingMicros = MILLISECONDS.toMicros(500L);
        System.out.println(remainingMicros);

        long start = System.currentTimeMillis();
        System.out.println("\nstart: " + start);
        RateLimiter.sleepMicrosUninterruptibly(remainingMicros);
        long end = System.currentTimeMillis();
        System.out.println("end: " + end);
        System.out.println("duration: " + (end - start));
        System.out.println(saturatedAdd(start, 500L) - start);
    }

}