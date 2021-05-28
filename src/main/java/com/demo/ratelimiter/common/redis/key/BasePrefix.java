package com.demo.ratelimiter.common.redis.key;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public abstract class BasePrefix implements KeyPrefix {
    private final int expireSeconds;

    private final String prefix;

    /**
     * 默认0代表永不过期
     */
    public BasePrefix(String prefix) {
        this.expireSeconds = 0;
        this.prefix = prefix;
    }

    @Override
    public int expireSeconds() {
        return expireSeconds;
    }

    @Override
    public String getPrefix() {
        String className = getClass().getSimpleName();
        return className + ":" + prefix;
    }
}
