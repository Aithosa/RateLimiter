package com.demo.ratelimiter;

import com.demo.ratelimiter.model.TAirRatelimitConf;
import com.demo.ratelimiter.model.TAirRatelimitConfMapper;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class myBatisTest extends RateLimiterFactoryTest{
    @Autowired
    private TAirRatelimitConfMapper TAirRatelimitConfMapper;

    @Test
    public void getConfTest() throws Exception {
        List<TAirRatelimitConf> rateLimiterConfList = TAirRatelimitConfMapper.getAirRatelimitConf();
        System.out.println(rateLimiterConfList);
    }
}
