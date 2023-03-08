package com.jack.utils.mapper;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jack.utils.excel.EntityLambdaUtils;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 抽取通用的in查询逻辑
 * @author jack
 * @since 1.0
 */
@Slf4j
public final class InSearch {

    private InSearch() {};

    private static final ConversionService CONVERSION_SERVICE = new DefaultConversionService();

    /**
     * in查询最大查询条件的个数
     */
    private static final int MAX_IN_QUERY_ITEM = 1000;

    /**
     * ignore. 参考{@link #fillDetail(List, Entry, BaseMapper, EntityLambdaUtils.Column, EntityLambdaUtils.Column)}
     */
    public static <T, E, SK, SV, TK, TV> void fillDetail(List<E> dataList,
                                                         Entry<SK, SV, TK, TV> dataEntry,
                                                         BaseMapper<T> mapper,
                                                         EntityLambdaUtils.Column<T, ?> matchColumn) {
        fillDetail(dataList, dataEntry, mapper, matchColumn, null);
    }

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
     *     InSearch.fillDetail(bookList,
     * 		    new InSearch.Entry<>(Book::getAuthorId, Book::getAuthor),
     * 		    authorMapper,
     * 		    Author::getId);
     * </pre></blockquote>
     * 如果说Book（书籍）的临时字段author保存的并不是对象Author(作者)，而只是作者的名字，即Author的name字段。
     * 那么可以指定取查询结果的name字段，填充到Book（书籍）的临时字段author。写法如下：
     * <blockquote><pre>
     *      InSearch.fillDetail(bookList,
 * 				new InSearch.Entry<>(Book::getAuthorId, Book::getAuthor),
 * 				authorMapper,
 * 				Author::getId,
 * 				Author::getName);
     * </pre></blockquote>
     *
     * @param dataList      数据的集合，对应例子中的Book（书籍）列表
     * @param dataEntry     要取实体类的哪个字段进行查询，结果填充到实体类的哪个字段。key：对应例子中Book（书籍）的authorId字段。value：对应例子中Book（书籍）的临时字段author
     * @param mapper        用于查询目标数据的mapper，对应例子中Author(作者)的mapper
     * @param matchColumn   目标数据用于匹配的字段的get方法，对应例子中Author(作者)的id字段
     * @param embedColumn   如果要填充的数据不是直接查询出来的目标数据，而是其某一字段，通过此参数指定
     */
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

        LambdaQueryWrapper<T> inQueryWrapper = assembleInQueryWrapper(matchColumn, sourceSet);
        List entityList = mapper.selectList(inQueryWrapper);
        if (CollectionUtils.isEmpty(entityList)) {
            log.info("===>entityList is empty. no data from database. sourceSet = {}", sourceSet);
            return;
        }

        String matchFieldName = EntityLambdaUtils.getFieldByFunction(matchColumn);
        Field matchField = null;

        String sourceFieldName = EntityLambdaUtils.getFieldByFunction(sourceColumn);
        Field sourceField = null;

        String targetFieldName = EntityLambdaUtils.getFieldByFunction(targetColumn);
        Field targetField = null;

        boolean needEnsureEmbedColumnClass = true;      // true：检验待填充字段，是否和mapper的泛型类型一致

        String embedColumnName = null;
        Field embedField = null;

        for (E data : dataList) {
            for (Object entity : entityList) {
                if (Objects.isNull(matchField)) {
                    matchField = ReflectionUtils.findField(entity.getClass(), matchFieldName);
                }

                assert matchField != null;
                matchField.setAccessible(true);
                Object matchFieldValue = matchField.get(entity);

                if (Objects.isNull(sourceField)) {
                    sourceField = ReflectionUtils.findField(data.getClass(), sourceFieldName);
                }

                Object sourceValue = sourceField.get(data);
                if (Objects.isNull(sourceValue)) {
                    continue;
                }

                Object convertMatchFieldValue = CONVERSION_SERVICE.convert(matchFieldValue, sourceValue.getClass());
                if (!sourceValue.equals(convertMatchFieldValue)) {
                    continue;
                }

                if (Objects.isNull(targetField)) {
                    targetField = ReflectionUtils.findField(data.getClass(), targetFieldName);
                }

                if (Objects.isNull(targetField)) {
                    throw new IllegalArgumentException(data.getClass().getName() + " not exist column: " + targetFieldName);
                }

                if (needEnsureEmbedColumnClass) {
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
                    Class<?> targetFieldType = targetField.getType();
                    if (embedColumn == null && !targetFieldType.equals(actualClass)) {
                        log.error("need Class = {}, but found = {}", targetFieldType.getName(), actualClassName);
                        StringBuilder builder = new StringBuilder("要填充的字段")
                                .append(targetFieldName).append("类型为").append(targetFieldType.getName())
                                .append("，但是提供的mapper的泛型类型却是").append(actualClassName);
                        throw new IllegalArgumentException(builder.toString());
                    }

                    needEnsureEmbedColumnClass = false;
                }

                if (embedColumn != null && Objects.isNull(embedColumnName)) {
                    embedColumnName = EntityLambdaUtils.getFieldByFunction(embedColumn);
                }

                if (StringUtils.isNotBlank(embedColumnName) && Objects.isNull(embedField)) {
                    embedField = ReflectionUtils.findField(entity.getClass(), embedColumnName);
                }

                fillTargetValue(entity, data, embedColumn, embedColumnName, embedField, targetField);
                break;
            }
        }
    }

    /**
     * 组装in查询
     * <p></p>
     * 由于in查询存在长度限制，此方法会对sql进行优化
     * <p></p>
     *
     * @param matchColumn   用于查询的列对应的实体类字段
     * @param sourceSet     取值集合
     * @return  in查询的QueryWrapper
     */
    private static <T> LambdaQueryWrapper<T> assembleInQueryWrapper(EntityLambdaUtils.Column<T, ?> matchColumn, Set<Object> sourceSet) {
        if (CollectionUtils.isEmpty(sourceSet)) {
            throw new IllegalArgumentException("sourceSet can not be empty");
        }

        LinkedHashSet<Object> totalValueSet = new LinkedHashSet<>(sourceSet);

        int pageSize = (totalValueSet.size() % MAX_IN_QUERY_ITEM) == 0
                ?  (totalValueSet.size() / MAX_IN_QUERY_ITEM)
                : (totalValueSet.size() / MAX_IN_QUERY_ITEM) + 1;
        if (pageSize == 1) {
            return new LambdaQueryWrapper<T>().in(matchColumn, sourceSet);
        }

        LinkedHashSet<Object> firstSet = truncateValueSet(0, totalValueSet);
        LambdaQueryWrapper<T> inQueryWrapper = new LambdaQueryWrapper<T>().in(matchColumn, firstSet);

        for (int i=1; i<pageSize; i++) {
            LinkedHashSet<Object> targetValueSet = truncateValueSet(i, totalValueSet);
            inQueryWrapper.or().in(matchColumn, targetValueSet);
        }

        return inQueryWrapper;
    }

    /**
     * 截取"一页"长度的元素
     *
     * @param index         页码。从0开始
     * @param totalValueSet 总的元素集合
     * @return 一页元素的集合
     */
    private static LinkedHashSet<Object> truncateValueSet(int index, LinkedHashSet<Object> totalValueSet) {
        if (CollectionUtils.isEmpty(totalValueSet)) {
            throw new IllegalArgumentException("totalValueSet can not be empty");
        }

        // [start, end)
        int start = index * MAX_IN_QUERY_ITEM;
        if (start >= totalValueSet.size()) {
            return new LinkedHashSet<>();
        }

        start = Math.min((start), (totalValueSet.size() - 1));
        int end = Math.min((index + 1) * MAX_IN_QUERY_ITEM, (totalValueSet.size()));

        Object[] totalValueArray = totalValueSet.toArray();
        return new LinkedHashSet<>(Arrays.asList(totalValueArray).subList(start, end));
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
     *
     * @param value        要填充的值
     * @param item         实体类
     * @param embedColumn  目标字段的内嵌字段
     * @param embedColumnName   内嵌字段名称
     * @param embedField    内嵌字段Field对象
     * @param targetField   目标字段Field对象
     */
    @SneakyThrows(IllegalAccessException.class)
    private static <T, E> void fillTargetValue(Object value,
                                               E item,
                                               EntityLambdaUtils.Column<T, ?> embedColumn,
                                               String embedColumnName,
                                               Field embedField,
                                               Field targetField) {
        targetField.setAccessible(true);
        if (embedColumn == null) {
            ReflectionUtils.setField(targetField, item, value);
            return;
        }

        if (Objects.isNull(embedField)) {
            throw new IllegalArgumentException(value.getClass().getName() + " not exist column: " + embedColumnName);
        }

        embedField.setAccessible(true);
        Object embedValue = embedField.get(value);
        Object transformValue;
        try {
            transformValue = tryToTransformTargetType(embedValue, targetField.getType());
        } catch (Exception e) {
            log.error("===>transform happen error. '{}' need data type: {}, buf found: {}",
                    embedColumnName, targetField.getType().getName(), embedField.getType().getName());
            throw new IllegalArgumentException("类型转换失败", e);
        }

        ReflectionUtils.setField(targetField, item, transformValue);
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
