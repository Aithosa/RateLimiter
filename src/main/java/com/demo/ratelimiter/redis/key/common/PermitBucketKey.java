package com.demo.ratelimiter.redis.key.common;

import com.demo.ratelimiter.redis.key.BasePrefix;

public class PermitBucketKey extends BasePrefix {
    private PermitBucketKey(int expireSeconds, String prefix) {
        super(expireSeconds, prefix);
    }

    public static PermitBucketKey permitBucket = new PermitBucketKey(0, "RL");
}
