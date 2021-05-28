package com.demo.ratelimiter.common.redis.key;

public interface KeyPrefix {
    int expireSeconds();

    String getPrefix();
}
