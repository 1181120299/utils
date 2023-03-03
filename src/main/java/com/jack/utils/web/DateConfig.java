package com.jack.utils.web;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Configuration
@Slf4j
public class DateConfig {

    @Bean
    public Converter<String, Date> addNewConvert(){
        return new Converter<String, Date>() {
            @Override
            public Date convert(String source) {
                Date date = null;
                try {
                    date = resolver(source);
                }catch (Exception e){
                    e.printStackTrace();
                }

                return date;
            }
        };
    }

    public Date resolver(String dateStr) throws ParseException {
        SimpleDateFormat sdf;
        if (StringUtils.isBlank(dateStr)) {
            return null;
        } else if (dateStr.matches("^\\d{4}-\\d{1,2}$")) {
            sdf = new SimpleDateFormat("yyyy-MM");
            return sdf.parse(dateStr);
        } else if (dateStr.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd");
            System.out.println(dateStr);
            return sdf.parse(dateStr);
        } else if (dateStr.matches("^\\d{4}-\\d{1,2}-\\d{1,2} {1}\\d{1,2}:\\d{1,2}$")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            return sdf.parse(dateStr);
        } else if (dateStr.matches("^\\d{4}-\\d{1,2}-\\d{1,2} {1}\\d{1,2}:\\d{1,2}:\\d{1,2}$")) {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(dateStr);
        } else if (dateStr.matches("^\\d{1,2}:\\d{1,2}$")) {
            sdf = new SimpleDateFormat("HH:mm");
            return sdf.parse(dateStr);
        }

        log.info("不支持的日期格式：{}", dateStr);
        return null;
    }

}
