package com.jack.utils.mapper;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.utils.excel.EntityLambdaUtils;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
public final class InSearch {

    private InSearch() {};

    private static final ConversionService CONVERSION_SERVICE = new DefaultConversionService();

    /**
     * 抽取共性的字段查询填充。
     * <p></p>
     * 假设有两个类：Book（书籍）和Author(作者)。
     * Book（书籍）中有一个authorId字段，保存了Author(作者)表的主键id，
     * 如果Book（书籍）列表要展示Author(作者)的所有信息，那么查询逻辑可以是这样的：
     * <p></p>
     * （1）查询出Book（书籍）列表数据
     * <P></P>
     * （2）提取Book（书籍）中的authorId字段，根据id查询Author(作者)信息
     * <p></p>
     * （3）将查询到的Author(作者)信息，保存到Book（书籍）的临时字段author中，返回页面进行渲染。
     * <p></p>
     * 此例子使用本方法实现，写法是这样的：
     * <P></P>
     * <blockquote><pre>
     *     InSearch.fillDetail(page.getRecords(),
     * 		    new InSearch.Entry<>(Book::getAuthorId, Book::getAuthorMessage),
     * 		    authorMapper,
     * 		    Author::getId);
     * </pre></blockquote>
     *
     * @param dataList      数据的集合，对应例子中的Book（书籍）列表
     * @param dataEntry     要取实体类的哪个字段进行查询，结果填充到实体类的哪个字段。key：对应例子中Book（书籍）的authorId字段。value：对应例子中Book（书籍）的临时字段author
     * @param mapper        用于查询目标数据的mapper，对应例子中Author(作者)的mapper
     * @param matchColumn   目标数据用于匹配的字段的get方法，对应例子中Author(作者)的id字段
     */
    public static <T, E, SK, SV, TK, TV> void fillDetail(List<E> dataList,
                                                         Entry<SK, SV, TK, TV> dataEntry,
                                                         BaseMapper<T> mapper,
                                                         EntityLambdaUtils.Column<T, ?> matchColumn) {
        fillDetail(dataList, dataEntry, mapper, matchColumn, null);
    }

    @SneakyThrows(IllegalAccessException.class)
    public static <T, E, SK, SV, TK, TV> void fillDetail(List<E> dataList,
                                                         Entry<SK, SV, TK, TV> dataEntry,
                                                         BaseMapper<T> mapper,
                                                         EntityLambdaUtils.Column<T, ?> matchColumn,
                                                         EntityLambdaUtils.Column<T, ?> embedColumn) {
        Assert.notNull(dataEntry, "dataEntry can not be null");

        EntityLambdaUtils.Column<SK, SV> sourceColumn = dataEntry.getKey();
        EntityLambdaUtils.Column<TK, TV> targetColumn = dataEntry.getValue();
        Assert.notNull(sourceColumn, "dataEntry key can not be null");
        Assert.notNull(targetColumn, "dataEntry value can not be null");

        Assert.notNull(mapper, "mapper can not be null");
        Assert.notNull(matchColumn, "matchColumn can not be null");

        if (CollectionUtils.isEmpty(dataList)) {
            log.info("===>dataList is empty");
            return;
        }

        Set<Object> sourceSet = new HashSet<>();
        for (E data : dataList) {
            String sourceFieldName = EntityLambdaUtils.getFieldByFunction(sourceColumn);
            Field sourceField = ReflectionUtils.findField(data.getClass(), sourceFieldName);
            if (Objects.isNull(sourceField)) {
                throw new IllegalArgumentException(data.getClass().getName() + " not exist column: " + sourceFieldName);
            }

            sourceField.setAccessible(true);
            Object sourceValue = sourceField.get(data);
            if (sourceValue != null) {
                sourceSet.add(sourceValue);
            }
        }

        if (CollectionUtils.isEmpty(sourceSet)) {
            log.info("===>sourceSet is empty. don`t need query database.");
            return;
        }

        List entityList = mapper.selectList(new LambdaQueryWrapper<T>()
                .in(matchColumn, sourceSet));
        if (CollectionUtils.isEmpty(entityList)) {
            log.info("===>entityList is empty. no data from database. sourceSet = {}", sourceSet);
            return;
        }

        for (Object entity : entityList) {
            String matchFieldName = EntityLambdaUtils.getFieldByFunction(matchColumn);
            Field matchField = ReflectionUtils.findField(entity.getClass(), matchFieldName);
            assert matchField != null;
            matchField.setAccessible(true);
            Object matchFieldValue = matchField.get(entity);

            for (E data : dataList) {
                String sourceFieldName = EntityLambdaUtils.getFieldByFunction(sourceColumn);
                Field sourceField = ReflectionUtils.findField(data.getClass(), sourceFieldName);

                Object sourceValue = sourceField.get(data);
                if (Objects.isNull(sourceValue) || !sourceValue.equals(matchFieldValue)) {
                    continue;
                }

                fillTargetValue(targetColumn, mapper, entity, data, embedColumn);
            }
        }
    }

    @EqualsAndHashCode
    @ToString
    public static final class Entry<SK, SV, TK, TV> implements Map.Entry<EntityLambdaUtils.Column<SK, SV>, EntityLambdaUtils.Column<TK, TV>> {

        EntityLambdaUtils.Column<SK, SV> key;
        EntityLambdaUtils.Column<TK, TV> value;

        public Entry(EntityLambdaUtils.Column<SK, SV> source, EntityLambdaUtils.Column<TK, TV> target) {
            this.key = source;
            this.value = target;
        }

        @Override
        public EntityLambdaUtils.Column<SK, SV> getKey() {
            return key;
        }

        @Override
        public EntityLambdaUtils.Column<TK, TV> getValue() {
            return value;
        }

        @Override
        public EntityLambdaUtils.Column<TK, TV> setValue(EntityLambdaUtils.Column<TK, TV> value) {
            return this.value = value;
        }
    }

    /**
     * 填充实体类的目标字段
     * @param targetColumn  目标字段get方法
     * @param mapper    目标字段实体类的mapper
     * @param value     要填充的值
     * @param item      实体类
     * @param embedColumn   目标字段的内嵌字段
     */
    @SneakyThrows(IllegalAccessException.class)
    private static <T, E, TK, TV> void fillTargetValue(EntityLambdaUtils.Column<TK, TV> targetColumn,
                                                       BaseMapper<T> mapper,
                                                       Object value,
                                                       E item,
                                                       EntityLambdaUtils.Column<T, ?> embedColumn) {
        String targetFieldName = EntityLambdaUtils.getFieldByFunction(targetColumn);
        Field targetField = ReflectionUtils.findField(item.getClass(), targetFieldName);
        if (Objects.isNull(targetField)) {
            throw new IllegalArgumentException(item.getClass().getName() + " not exist column: " + targetFieldName);
        }

        Class<?> targetFieldType = targetField.getType();
        Class<?> actualClass = null;

        Type type = ((Class<?>) mapper.getClass().getGenericInterfaces()[0]).getGenericInterfaces()[0];
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            actualClass = (Class<?>) actualTypeArguments[0];
        } else {
            log.info("===>mapper没有泛型信息");
        }

        String actualClassName = Objects.isNull(actualClass) ? "null" : actualClass.getName();
        if (embedColumn == null && !targetFieldType.equals(actualClass)) {
            log.error("need Class = {}, but found = {}", targetFieldType.getName(), actualClassName);
            StringBuilder builder = new StringBuilder("要填充的字段")
                    .append(targetFieldName).append("类型为").append(targetFieldType.getName())
                    .append("，但是提供的mapper的泛型类型却是").append(actualClassName);
            throw new IllegalArgumentException(builder.toString());
        }

        targetField.setAccessible(true);
        if (embedColumn == null) {
            ReflectionUtils.setField(targetField, item, value);
        } else {
            String embedColumnName = EntityLambdaUtils.getFieldByFunction(embedColumn);
            Field embedField = ReflectionUtils.findField(value.getClass(), embedColumnName);
            if (Objects.isNull(embedField)) {
                throw new IllegalArgumentException(value.getClass().getName() + " not exist column: " + embedColumnName);
            }

            embedField.setAccessible(true);
            Object embedValue = embedField.get(value);
            Object transformValue;
            try {
                transformValue = tryToTransformTargetType(embedValue, targetFieldType);
            } catch (Exception e) {
                log.error("===>transform happen error. '{}' need data type: {}, buf found: {}",
                        embedColumnName, targetFieldType.getName(), embedField.getType().getName());
                throw new IllegalArgumentException("类型转换失败", e);
            }

            ReflectionUtils.setField(targetField, item, transformValue);
        }
    }

    /**
     * 将数据转换成指定的类型
     * @param embedValue    数据
     * @param targetFieldType   类型class
     * @return  转换后的数据
     */
    private static <T> T tryToTransformTargetType(Object embedValue, Class<T> targetFieldType) {
        return CONVERSION_SERVICE.convert(embedValue, targetFieldType);
    }
}
