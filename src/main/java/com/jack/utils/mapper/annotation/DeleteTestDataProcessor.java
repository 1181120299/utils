package com.jack.utils.mapper.annotation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "jack.mapper", name = "delete-test-data", havingValue = "true")
@EnableConfigurationProperties(MapperProperties.class)
public class DeleteTestDataProcessor {

    /**
     * 主键的列名
     */
    private static final String PK_NAME = "id";

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 监听spring应用上下文started事件。完成测试数据删除操作。
     */
    @EventListener({ContextRefreshedEvent.class})
    @Transactional
    public void processStartedEvent() {
        log.info("===>inject DeleteTestDataProcessor. will delete test data.");
        Map<String, Object> mapperMap = applicationContext.getBeansWithAnnotation(DeleteTestData.class);
        if (mapperMap.isEmpty()) {
            log.info("===>can not find DeleteTestData annotation. do not need delete test data");
            return;
        }

        mapperMap.entrySet().forEach(this::handleMapper);

        log.error("===>删除测试数据，执行完成。请将配置项jack.mapper.delete-test-data修改为false（或者删除）后，重启程序");
    }

    /**
     * 处理Mapper上关于删除测试数据的注解
     * @param entry key = name of mapper in ioc, value = mapper proxy
     */
    private void handleMapper(Map.Entry<String, Object> entry) {
        Object mapperProxyObj = entry.getValue();
        try {
            ParameterizedType type = (ParameterizedType)((Class<?>) (mapperProxyObj.getClass().getGenericInterfaces()[0])).getGenericInterfaces()[0];
            Class<?> baseMapperClazz = (Class<?>) type.getRawType();
            if (!BaseMapper.class.isAssignableFrom(baseMapperClazz)) {
                log.error("===>DeleteTestData annotation should put on Mapper.java, buf found: {}", baseMapperClazz.getName());
                throw new IllegalArgumentException("DeleteTestData注解只能标注在Mapper上。这不是一个Mapper：" + baseMapperClazz.getName());
            }
        } catch (Exception e) {
            log.error("===>DeleteTestData annotation should put on Mapper.java, buf found: {}", mapperProxyObj.getClass().getName());
            throw new IllegalArgumentException("DeleteTestData注解只能标注在Mapper上。这不是一个Mapper：" + mapperProxyObj.getClass().getName());
        }

        BaseMapper<?> baseMapper = (BaseMapper<?>) applicationContext.getBean(entry.getKey());
        QueryWrapper queryWrapper = assembleQueryWrapper(baseMapper);
        baseMapper.delete(queryWrapper);
    }

    /**
     * 根据删除测试数据相关的注解，组装删除数据的sql语句
     * @param baseMapperBean    数据库访问对象
     * @return  删除数据的sql语句
     */
    private QueryWrapper assembleQueryWrapper(BaseMapper<?> baseMapperBean) {
        Class targetMapperClazz = (Class) baseMapperBean.getClass().getGenericInterfaces()[0];
        DeleteTestData deleteTestData = (DeleteTestData) targetMapperClazz.getAnnotation(DeleteTestData.class);
        if (!deleteTestData.confirm()) {
            throw new IllegalStateException("请确认删除测试数据，以及保留数据的规则正确后，将DeleteTestData注解的confirm设置为true。请检查"
                    + targetMapperClazz.getName());
        }

        QueryWrapper selectQueryWrapper = new QueryWrapper();
        selectQueryWrapper.apply("1 = 2");
        if (deleteTestData.ids() != null && deleteTestData.ids().length > 0) {
            ((QueryWrapper)selectQueryWrapper.or()).in(PK_NAME, deleteTestData.ids());
        }

        if (StringUtils.isBlank(deleteTestData.column())) {
            log.debug("===>{}: DeleteTestData 'column' is empty. will ignore other property except 'ids'", targetMapperClazz.getName());
            return getDeleteQueryBySelectQuery(deleteTestData, selectQueryWrapper, baseMapperBean);
        }

        if (StringUtils.isNotBlank(deleteTestData.equals())) {
            switch (deleteTestData.equals()) {
                case DeleteTestData.SPECIAL_VALUE_NULL:
                    ((QueryWrapper)selectQueryWrapper.or()).apply(deleteTestData.column() + " is null");
                    break;
                case DeleteTestData.SPECIAL_VALUE_NOT_NULL:
                    ((QueryWrapper)selectQueryWrapper.or()).apply(deleteTestData.column() + " is not null");
                    break;
                case DeleteTestData.SPECIAL_VALUE_EMPTY:
                    ((QueryWrapper)selectQueryWrapper.or()).eq(deleteTestData.column(), "");
                    break;
                default:
                    ((QueryWrapper)selectQueryWrapper.or()).eq(deleteTestData.column(), deleteTestData.equals());
            }
        }

        if (StringUtils.isNotBlank(deleteTestData.greaterThan())
                && StringUtils.isNotBlank(deleteTestData.lessThan())) {
            selectQueryWrapper.or(wrapper -> {
                Object gtWrapper = ((QueryWrapper) wrapper).gt(deleteTestData.column(), deleteTestData.greaterThan());
                ((QueryWrapper)gtWrapper).lt(deleteTestData.column(), deleteTestData.lessThan());
            });
        } else {
            if (StringUtils.isNotBlank(deleteTestData.greaterThan())) {
                ((QueryWrapper)selectQueryWrapper.or()).gt(deleteTestData.column(), deleteTestData.greaterThan());
            }

            if (StringUtils.isNotBlank(deleteTestData.lessThan())) {
                ((QueryWrapper)selectQueryWrapper.or()).lt(deleteTestData.column(), deleteTestData.lessThan());
            }
        }

        if (StringUtils.isNotBlank(deleteTestData.like())) {
            ((QueryWrapper)selectQueryWrapper.or()).like(deleteTestData.column(), deleteTestData.like());
        }

        return getDeleteQueryBySelectQuery(deleteTestData, selectQueryWrapper, baseMapperBean);
    }

    // 过滤掉要保留的数据，返回要删除数据对应的sql
    @SneakyThrows(IllegalAccessException.class)
    private QueryWrapper getDeleteQueryBySelectQuery(DeleteTestData deleteTestData,
                                                     QueryWrapper selectQueryWrapper,
                                                     BaseMapper<?> baseMapper) {
        List needDataList;
        try {
            needDataList = baseMapper.selectList(selectQueryWrapper);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e.getMessage().contains("Unknown column")) {
                Class targetMapperClazz = (Class)baseMapper.getClass().getGenericInterfaces()[0];

                throw new IllegalArgumentException(targetMapperClazz.getName() + "对应的实体类中不存在字段"
                        + deleteTestData.column() + "，请检查DeleteTestData注解的column属性");
            }

            throw e;
        }

        QueryWrapper deleteQueryWrapper = new QueryWrapper();
        if (CollectionUtils.isNotEmpty(needDataList)) {
            Object item = needDataList.get(0);
            Field idField = ReflectionUtils.findField(item.getClass(), PK_NAME);
            idField.setAccessible(true);

            List idList = new ArrayList();
            for (Object entity : needDataList) {
                idList.add(idField.get(entity));
            }

            deleteQueryWrapper.notIn(PK_NAME, idList);
        }

        return deleteQueryWrapper;
    }
}
