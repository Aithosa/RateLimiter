package com.demo.ratelimiter.redis.key;

public interface KeyPrefix {
    int expireSeconds();

    String getPrefix();
}
