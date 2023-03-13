package com.jack.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jack.mapper")
@Data
public class MapperProperties {

    /**
     * 是否删除测试数据
     */
    boolean deleteTestData;

    /**
     * 接口响应格式中状态码的字段名称。默认：retCode
     */
    String responseCodeField;

    /**
     * 接口响应格式中信息字段名称。默认：retMsg
     */
    String responseMessageField;

    /**
     * 接口响应格式中数据字段名称。默认：data
     */
    String responseDataField;

    /**
     * 接口响应格式中正常的状态码值。默认：2000
     */
    Integer responseCorrectCode;

    /**
     * 接口响应格式中异常的状态码值。默认：2999
     */
    Integer responseErrorCode;
}
