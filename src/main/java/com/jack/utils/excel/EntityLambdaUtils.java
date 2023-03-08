package com.jack.utils.excel;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 使用lambda表达式操作实体类字段的帮助类
 */
public class EntityLambdaUtils {

    /**
     * 返回方法引用指定的实体类字段名
     * @param func  实体类的字段Get方法
     * @return  字段名
     * @param <T>   实体类this对象
     * @param <R>   Get方法返回值类型
     */
    public static <T, R> String getFieldByFunction(SFunction<T, R> func) {
        Method writeReplace;
        try {
            writeReplace = func.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object invoke = writeReplace.invoke(func);

            SerializedLambda serializedLambda = (SerializedLambda) invoke;

            String implMethodName = serializedLambda.getImplMethodName();
            if (!implMethodName.startsWith("get")) {
                throw new IllegalArgumentException("entity method is not start with get");
            }

            String fieldName = implMethodName.substring(3);
            fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
            return fieldName;
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface Column<T, R> extends SFunction<T, R>, Serializable {}
}
