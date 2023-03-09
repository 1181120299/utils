package com.jack.utils.mapper.annotation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jack.mapper")
@Data
public class MapperProperties {

    /**
     * 是否删除测试数据
     */
    boolean deleteTestData;
}
