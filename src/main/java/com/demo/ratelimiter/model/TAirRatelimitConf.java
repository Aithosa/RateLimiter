package com.demo.ratelimiter.model;

import lombok.Data;

@Data
public class TAirRatelimitConf {
    private String channelType;

    private String interfaceNo;

    private String interfaceUrl;

    private Integer rateLimit;

    private Float cache;

    private boolean status;
}
