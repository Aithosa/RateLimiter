package com.demo.ratelimiter;

import com.demo.ratelimiter.common.Constant;
import com.demo.ratelimiter.common.redis.service.RedisService;
import com.demo.ratelimiter.common.redis.service.RedissonService;
import com.demo.ratelimiter.origin.limiter.Limiter;
import com.demo.ratelimiter.origin.limiter.ratelimiter.RateLimiter;
import com.demo.ratelimiter.origin.limiter.ratelimiter.RateLimiterConfig;
import com.demo.ratelimiter.origin.limiter.ratelimiter.RateLimiterFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.math.LongMath.saturatedAdd;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RateLimiterApplication.class)
public class RateLimiterFactoryTest {
    /**
     * 限流器开关
     */
    private static final String rateLimitSwitch = Constant.ON.getCode();

    /**
     * 任务总数量
     */
    private static final int JOB_NUMS = 20;

    /**
     * 每秒发送请求限制
     */
    private static final long REQUEST_LIMIT_PER_SECONDS = 5L;

    /**
     * 缓冲区长度(相对于桶大小的比例)
     */
    private static final double CACHE = 0.5;

    /**
     * 缓冲任务数量
     */
    private static double CACHE_SIZE = 0L;

    /**
     * 超时时间
     */
    private static Long TIMEOUT = 0L;

    /**
     * 休眠时间，用于等待令牌恢复
     */
    private static final long SLEEP_TIME = 0L;

    /**
     * 统计用参数
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
        if (!rateLimiter.tryAcquire(rateLimitSwitch)) {
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
                System.out.println(i + ": success, left permit: " + rateLimiter.getBucketAndSync().getStoredPermits());
            } else {
                System.out.println(i + ": fail, left permit: " + rateLimiter.getBucketAndSync().getStoredPermits());
                try {
                    Thread.sleep(500L);
                    System.out.println("after sleep, permit: " + rateLimiter.getBucketAndSync().getStoredPermits());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println();
        }
    }

    @Test
    public void tryAcquireThreadTest() throws Exception {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("testPermitLimiter4", 5, redissonService.getRLock("testPermitLock2"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        ExecutorService executor = Executors.newFixedThreadPool(12);
        START_TIME = System.currentTimeMillis();
        for (int i = 0; i < 10; ++i) {
            int finalI = i;
            executor.execute(() -> {
                System.out.println("job " + finalI + ": " + rateLimiter.tryAcquire(400L) + " cost: " + (System.currentTimeMillis() - START_TIME));
            });
        }

        Thread.sleep(2000L);
        executor.shutdown();
    }

    @Test
    public void acquireTest() throws Exception {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("testPermitLimiter3", 2, redissonService.getRLock("testPermitLock3"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        for (int i = 1; i <= 10; i++) {
            System.out.println("acquire time: " + rateLimiter.acquire());
        }
    }

    /**
     * 同步的for是计算的获取方法开始到结束的时间，但异步因为几个线程是一起启动的，所以最后完成的请求等待的时间就越长
     * @throws Exception
     */
    @Test
    public void acquireAsyncTest() throws Exception {
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig("testPermitLimiter4", 5, redissonService.getRLock("testPermitLock3"), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 1; i <= 10; i++) {
            executor.execute(() -> System.out.println("acquire time: " + rateLimiter.acquire()));

        }
        Thread.sleep(2000L);
        executor.shutdown();
    }

    /**
     * 测试tryAcquire()等待时间
     * NOTE: 用for循环调用的时候不会达到预期效果，出在超时时间上，每次都要休眠200ms(理论上)，所以永远不会超过超时时间500ms
     */
    @Test
    public void tryAcquireTimeTest() throws Exception {
        String name = "testLimiterTryAcquireTime";
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig(name, 5, 0.5F,redissonService.getRLock(name), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);
        int success = 0;
        int fail = 0;

        long start = System.currentTimeMillis();
        for (int i = 1; i <= 10; i++) {
            if (rateLimiter.tryAcquire()) {
                System.out.println("cost: " + (System.currentTimeMillis() - start));
                success++;
                System.out.println(i + ": success");
            } else {
                fail++;
                System.out.println(i + ": fail");
            }
        }
        System.out.println("success: " + success);
        System.out.println("fail: " + fail);
    }

    @Test
    public void tryAcquireTimeAsyncTest() throws Exception {
        String name = "testLimiterTryAcquireTimeAsync";
        RateLimiterFactory factory = new RateLimiterFactory();
        RateLimiterConfig config = new RateLimiterConfig(name, 5, 0.5F,redissonService.getRLock(name), redisService);
        RateLimiter rateLimiter = factory.getPermitLimiter(config);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        long start = System.currentTimeMillis();
        System.out.println("开始时间： " + start);
        for (int i = 1; i <=10; i++) {
            int finalI = i;
            executor.execute(() -> {
                long time1 = (System.currentTimeMillis() - start) * 1000;
                System.out.println("任务： " + finalI + "即将启动， 花费: " + time1);
                if (rateLimiter.tryAcquire()) {
                    System.out.println("任务： " + finalI + "令牌获取成功: " + System.currentTimeMillis());
                    long time2 = (System.currentTimeMillis() - start) * 1000;;
                    System.out.println("任务： " + finalI + "完成: " + time2);
//                    System.out.println("finalI: " + finalI + " cost: " + (System.currentTimeMillis() - start));
                    System.out.println("任务： " + finalI + "执行了: " + (time2 - time1));
                    success.getAndIncrement();
                    System.out.println(finalI + ": success");
                    System.out.println();
                } else {
                    fail.getAndIncrement();
                    System.out.println(finalI + ": fail");
                    System.out.println();
                }
            });
        }
        Thread.sleep(2000L);
        System.out.println("success: " + success);
        System.out.println("fail: " + fail);
        executor.shutdown();
    }

    /**
     * 测试时间相关
     */
    @Test
    public void timeTest() {
        long remainingMicros = MILLISECONDS.toMicros(500L);
        System.out.println(remainingMicros);

        long start = System.currentTimeMillis();
        System.out.println("\nstart: " + start);
        Limiter.sleepMicrosUninterruptibly(remainingMicros);
        long end = System.currentTimeMillis();
        System.out.println("end: " + end);
        System.out.println("duration: " + (end - start));
        System.out.println(saturatedAdd(start, 500L) - start);
    }

    @Test
    public void tryFinallyTest() {
        boolean condition = false;
        String wait = "";
        System.out.println("lock");
        try {
            if (condition) {
                System.out.println("can't acquire!");
                System.out.println("return false");
            } else {
                wait = "need to wait";
            }
        } finally {
            System.out.println("unlock");
        }
        System.out.println(wait);
        System.out.println("return true");
    }
}