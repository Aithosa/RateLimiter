package com.demo.ratelimiter.model;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TAirRatelimitConfMapper {
    List<TAirRatelimitConf> getAirRatelimitConf();
}
