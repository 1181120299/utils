package com.jack.utils.mapper.annotation;

import java.lang.annotation.*;

/**
 * 删除测试数据
 * <p></p>
 * 项目在测试阶段，正式上线运行之前，或多或少都要执行清除测试数据的操作。
 * <p></p>
 * 在需要清理测试数据的数据库操作对象（Mapper）上添加此注解，
 * 当配置项<b><i>jack.mapper.delete-test-data = true</i></b>时，
 * 启动项目会执行数据清理操作
 * <p></p>
 * 默认会删除表中的所有数据，可以通过设置条件过滤掉<b><i>需要保留</i></b>的数据
 * <p></p>
 * <b><i>注意：不支持复合主键，并且表的主键列名必须为id。目前只在mysql数据库测试通过，oracle数据库待测试。</i></b>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface DeleteTestData {

    /**
     * 列的值为 null
     */
    String SPECIAL_VALUE_NULL = "#null";

    /**
     * 列有值（包含空字符串），即不为 null
     */
    String SPECIAL_VALUE_NOT_NULL = "#notNull";

    /**
     * 列的值为空字符串，即：''
     */
    String SPECIAL_VALUE_EMPTY = "#empty";

    /**
     * 确认删除测试数据，以及保留数据规则正确，则置为true
     * <p></p>
     * 用于预防自动生成代码后，开发人员忘记配置数据保留规则，导致清除全部数据的情况
     * @return  确认状态
     * @throws IllegalStateException    如果运行时仍为false（未确认）
     */
    boolean confirm() default false;

    /**
     * 数据表的列名，通过此列过滤掉需要保留的数据
     * @return  列名
     */
    String column() default "";

    /**
     * 指定列的值等于某个值时，保留此数据
     * <p></p>
     * 如果是特殊的值（例如为null），请使用：
     * <P></P>
     * {@link #SPECIAL_VALUE_NULL}
     * <P></P>
     * {@link #SPECIAL_VALUE_NOT_NULL}
     * <P></P>
     * {@link #SPECIAL_VALUE_EMPTY}
     * <P></P>
     *
     * @return  某个值
     */
    String equals() default "";

    /**
     * 指定列的值大于某个值时（不包含），保留此数据
     * @return  某个值
     */
    String greaterThan() default "";

    /**
     * 指定列的值小于某个值时（不包含），保留此数据
     * @return  某个值
     */
    String lessThan() default "";

    /**
     * 指定列的对应的主键id为数组中指定的值时，保留此数据
     * <p></p>
     * 注意：不支持复合主键
     * @return  主键id数组
     */
    String[] ids() default {};

    /**
     * 指定列的值像某个值时，保留此数据
     * @return  某个值
     */
    String like() default "";
}
