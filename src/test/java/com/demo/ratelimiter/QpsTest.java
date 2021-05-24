package com.demo.ratelimiter;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QpsTest {
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

    private final QpsLimiter qpsLimiter = new QpsLimiter(REQUEST_LIMIT_PER_SECONDS, SLEEP_TIME, CACHE);

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
}
