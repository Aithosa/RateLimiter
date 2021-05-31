package com.demo.ratelimiter;

import com.demo.ratelimiter.guava.GuavaLimiter;
import com.google.common.util.concurrent.RateLimiter;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GuavaLimiterTest extends RateLimiterFactoryTest {
    private static final int JOB_NUMS = 20;

    /**
     * 每秒发送请求限制
     */
    private static final String REQUEST_LIMIT_PER_SECONDS = "5";

    /**
     * 缓冲区长度
     */
    private static final double CACHE = 0.5;

    private static final long SLEEP_TIME = 1000L;

    private final GuavaLimiter qpsLimiter = new GuavaLimiter(REQUEST_LIMIT_PER_SECONDS, SLEEP_TIME, CACHE);

    /**
     * 测试qps, 缓冲队列长度
     */
    @Test
    public void qpsTest() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 0; i < JOB_NUMS; ++i) {
            int finalI = i;
            executor.execute(() -> {
                String rsp = qpsLimiter.acquireJob(finalI);
            });
        }

        Thread.sleep(2500L);
        qpsLimiter.jobCountStatistic(JOB_NUMS);

        executor.shutdown();
    }

    @Test
    public void guavaAcquireTest() throws Exception {
        RateLimiter rateLimiter = RateLimiter.create(3);
        ExecutorService executor = Executors.newFixedThreadPool(7);

        for (int i = 0; i < 6; ++i) {
            int finalI = i;
            executor.execute(() -> System.out.println("job " + finalI + ": " + rateLimiter.acquire()));
        }

        Thread.sleep(2500L);
        executor.shutdown();
    }
}
