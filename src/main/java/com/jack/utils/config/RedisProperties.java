package com.jack.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jack.redis")
@Data
public class RedisProperties {
    /**
     * redis缓存过期时间。单位：秒
     */
    private int timeToLive = 60;
}
