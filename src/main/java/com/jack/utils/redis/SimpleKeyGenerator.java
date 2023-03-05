package com.jack.utils.redis;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;

import java.lang.reflect.Method;

public class SimpleKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return generateKey(method, params);
    }

    /**
     * Generate a key based on the specified parameters.
     * <p></p>
     * method name will be used as first param
     */
    public static Object generateKey(Method method, Object... params) {
        String methodName = method.getName();

        Object[] newParamArray = new Object[params.length + 1];
        newParamArray[0] = methodName;
        System.arraycopy(params, 0, newParamArray, 1, params.length);

        if (newParamArray.length == 1) {
            Object paramItem = newParamArray[0];
            if (paramItem != null && !paramItem.getClass().isArray()) {
                return paramItem;
            }
        }

        return new SimpleKey(newParamArray);
    }

}