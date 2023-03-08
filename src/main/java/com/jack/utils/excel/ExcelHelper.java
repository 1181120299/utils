package com.jack.utils.excel;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

/**
 * excel帮助类
 * @param <T>   导出到excel的数据实体类
 * @author chenjiabao
 * @see EntityLambdaUtils
 */
@Slf4j
public abstract class ExcelHelper<T> {

    /**
     * ignored
     * @see #addBlankRow(List, int, EntityLambdaUtils.Column) 
     */
    public final List<T> addBlankRow(List<T> dataList, int pageSize) throws NoSuchFieldException, InstantiationException, IllegalAccessException {
        return addBlankRow(dataList, pageSize, null);
    }

    /**
     * ignored
     * @see #addBlankRow(List, int, EntityLambdaUtils.Column, int)
     */
    public final <R> List<T> addBlankRow(List<T> dataList, int pageSize, EntityLambdaUtils.Column<T, R> serialColumnName) throws NoSuchFieldException, InstantiationException, IllegalAccessException {
        return addBlankRow(dataList, pageSize, serialColumnName, 0);
    }

    /**
     * 往集合中加入空数据，目的是为了导出excel之后，优化打印效果。
     * <p>
     * 即：按页处理打印，最后不足一页的，补空行。
     * <p>
     * 用到了反射，请确保集合中的元素，具有无参构造器。
     * <p>
     * 使用示例：
     * <blockquote><pre>
     *     List< GoodsCheckdtlEntity > detailList = new ArrayList<>();
     *     ExcelHelper<GoodsCheckdtlEntity> helper = new ExcelHelper<GoodsCheckdtlEntity>(){};
     *     int pageSize = 15;
     *     helper.addBlankRow(detailList, pageSize, GoodsCheckdtlEntity::getSerial);
     * </pre></blockquote><p>
     * 上面这段代码，数据集合detailList没有元素，然后excel每一页要求15条数据，意味着会插入15条空数据凑齐一页，序号自增。
     * @param dataList  需要补空数据的集合
     * @param pageSize  一页excel的数据量。可通过excel的打印预览确定
     * @param serialColumnName  序号字段。如果导出的excel空白行，需要序号继续增加的话。会将序号从1开始重新排序。
     * @param headerRowNum  表头的行数（即数据行的前边有多少行）。如果只有第一页excel有表头，才需要加此参数计算最后一页的空白行数。
     * @return  补充了空数据的集合
     * @throws NoSuchFieldException 给定的序号字段不存在
     * @throws InstantiationException   因为需要反射创建集合中的元素
     * @throws IllegalAccessException   因为需要反射创建集合中的元素
     */
    @SuppressWarnings("unchecked")
    public final <R> List<T> addBlankRow(List<T> dataList,
                                   int pageSize,
                                   EntityLambdaUtils.Column<T, R> serialColumnName,
                                   int headerRowNum) throws NoSuchFieldException, InstantiationException, IllegalAccessException {
        if (dataList == null || dataList.size() == 0) {
            log.info("dataList is null. will new a list");
            dataList = new ArrayList<T>();
        }

        if (pageSize <=0) throw new IllegalArgumentException("pageSize should greater than zero. but found：{}" + pageSize);

        Class<T> clazz = (Class<T>)((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];


        Field serialField = null;
        int maxSerialValue = 0;
        if (serialColumnName != null) {
            serialField = clazz.getDeclaredField(EntityLambdaUtils.getFieldByFunction(serialColumnName));

            for (T item : dataList) {
                serialField.setAccessible(true);
                Object serialValue = serialField.get(item);
                if (serialValue == null) {
                    serialField.setAccessible(true);
                    serialField.set(item, ++maxSerialValue);
                    serialValue = serialField.get(item);
                }

                int currentSerialValue;
                try {
                    currentSerialValue = Integer.parseInt(serialValue.toString());
                } catch (NumberFormatException e) {
                    log.error("serial field must be number. but found: {}", serialValue, e);
                    throw new IllegalAccessException("serial field must be number. but found: " + serialValue);
                }

                if (currentSerialValue > maxSerialValue) {
                    maxSerialValue = currentSerialValue;
                }
            }
        }

        int listSize = dataList.size();
        int blankRowNum = pageSize - ((listSize + headerRowNum) % pageSize);
        log.info("should add blank row`s num = {}", blankRowNum);

        for (int i=0; i< blankRowNum; i++) {
            T newBlankItem = clazz.newInstance();
            if (serialField != null) {
                serialField.setAccessible(true);
                serialField.set(newBlankItem, ++maxSerialValue);
            }

            dataList.add(newBlankItem);
        }

        return dataList;
    }
}
