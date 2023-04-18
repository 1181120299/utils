package com.jack.utils.thread;

import org.springframework.boot.SpringBootVersion;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 如果SpringBoot 版本 >= 3.0，返回false
 * <p></p>
 *
 * 有一些组件，随着SpringBoot更新到3.0.0，出现兼容问题。可以选择不进行注入。
 */
public class SpringBootVersionCondiction implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String springBootVersion = SpringBootVersion.getVersion();
        return springBootVersion.compareTo("3.0.0") < 0;
    }
}
