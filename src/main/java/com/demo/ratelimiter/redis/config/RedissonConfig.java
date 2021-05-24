package com.demo.ratelimiter.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
public class RedissonConfig {
    @Value("${redis.host}")
    private String host;

    @Value("${redis.port}")
    private String port;
}
