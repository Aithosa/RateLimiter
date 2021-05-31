package com.demo.ratelimiter.origin.limiter.ratelimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

import static java.lang.Math.min;

/**
 * 令牌桶, 存放在Redis中的结构
 * 可以根据名称给每个接口做定义，直接在配置表中保存名称，用配置表里接口的名称
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermitBucket {
    /**
     * 唯一标识
     */
    private String name;

    /**
     * 最大存储令牌数
     */
    private long maxPermits;

    /**
     * 当前存储令牌数
     */
    private long storedPermits;

    /**
     * 每两次添加令牌之间的时间间隔（逐个添加令牌），单位为纳秒
     */
    private long intervalMicros;

    /**
     * 下一个获取令牌请求被批准的时间
     */
    private long nextFreeTicketMicros;

    /**
     * 更新当前持有的令牌数, 同步令牌桶的状态
     * 根据当前时间和上一次时间戳的间隔，更新令牌桶中当前令牌数。
     * 若当前时间晚于 nextFreeTicketMicros，则计算该段时间内可以生成多少令牌，将生成的令牌加入令牌桶中并更新数据
     *
     * @param nowMicros 当前时间
     */
    public void reSync(long nowMicros) {
        // 当前时间大于下次更新令牌的时间，才会执行更新，否则不变
        if (nowMicros > nextFreeTicketMicros) {
            // long newStoredPermits = Math.min(maxPermits, storedPermits + (now - lastUpdateTime) / intervalNanos);
            long newPermits = (nowMicros - nextFreeTicketMicros) / intervalMicros;
            storedPermits = min(maxPermits, storedPermits + newPermits);
            // 如果时间还不够生成新的令牌，不需要更新nextFreeTicketMicros
            if (newPermits > 0L) {
                nextFreeTicketMicros = nowMicros;
            }
        }
    }

    /**
     * 没有的话RedisService的方法执行有可能会报错，或者换成Jackson
     */
    public PermitBucket(String json) throws IOException {
        PermitBucket param = new ObjectMapper().readValue(json, PermitBucket.class);
        this.name = param.getName();
        this.maxPermits = param.getMaxPermits();
        this.storedPermits = param.getStoredPermits();
        this.intervalMicros = param.getIntervalMicros();
        this.nextFreeTicketMicros = param.getNextFreeTicketMicros();
    }
}
