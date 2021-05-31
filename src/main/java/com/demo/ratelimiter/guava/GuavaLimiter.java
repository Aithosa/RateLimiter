package com.demo.ratelimiter.guava;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.util.NumberUtils;

import java.util.concurrent.TimeUnit;

/**
 * 基于guava的单机算法
 *
 * @author w00585603
 */
public class GuavaLimiter {
    private static double CACHE = 0;

    /**
     * 缓冲区长度
     */
    private static double CACHE_SIZE = 0;

    /**
     * 超时时间
     */
    private static Long TIMEOUT = 0L;

    private static long SLEEP_TIME = 0L;

    private static RateLimiter rateLimiter;

    /**
     * 统计参数
     */
    private static long START_TIME = System.currentTimeMillis();

    private static int successJob = 0;

    private static int failJob = 0;

    /**
     * 可以从配置表读取配置，Apollo配置控制开关，开关状态变化会重新读配置
     * <p>
     * 问题：如何通过Apollo控制开关？
     * 1. 像对内对外那样通过控制参数决定采用哪个实例
     * 2. 在limit函数内通过参数控制
     * 具体实现待测试
     */
    public GuavaLimiter(String limit, long sleepTime, double cache) {
        int requestLimit = 5;
//        CACHE = (int) (requestLimit * cache);
        CACHE_SIZE = (int) (requestLimit * cache);
        TIMEOUT = (long) (CACHE_SIZE * (1000L / requestLimit));
//        TIMEOUT = (long) ((CACHE_SIZE + requestLimit) * (1000L / requestLimit));
        SLEEP_TIME = sleepTime;

        rateLimiter = RateLimiter.create(requestLimit);
        statistic();

        if (SLEEP_TIME > 0L) {
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        START_TIME = System.currentTimeMillis();
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

    /**
     * 通过rateLimiter控制接口调用
     */
    public String acquireJob(int jobNo) {
        if (!rateLimiter.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
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

    private static void statistic() {
        System.out.println("---------- statistic ----------");
        System.out.println("Request limit per seconds: " + rateLimiter.getRate());
        System.out.println("Cache size: " + CACHE_SIZE);
        System.out.println("Timeout: " + TIMEOUT + " ms");
        System.out.println("Sleep Time: " + SLEEP_TIME + " ms");
        System.out.println("-------------------------------\n");
    }

    private static void jobTimeStatistic(String req) {
        successJob++;
        long totalTime = System.currentTimeMillis() - START_TIME;
        System.out.println("job " + req + " takes " + totalTime + " ms to start.");
    }

    public void jobCountStatistic(int jobNums) {
        System.out.println("\ntotal " + jobNums + " jobs, success " + successJob + " jobs, fail " + failJob + " jobs.");
    }
}
