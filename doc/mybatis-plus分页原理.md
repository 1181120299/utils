mybatis-plus分页是基于拦截器实现的。需要提供一个实现了`InnerInterceptor`接口的拦截器。例如官方提供的`PaginationInnerInterceptor`。

# 一、注册分页拦截器

```java
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration      //表示这是一个配置类
public class MybatisPlusPageConfig {

    /**
     * 注册插件
     */
    @Bean   //表示此方法返回一个Bean实例
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //添加分页插件
        PaginationInnerInterceptor pageInterceptor = new PaginationInnerInterceptor();
        //设置请求的页面大于最大页的操作，true调回首页，false继续请求，默认是false
        pageInterceptor.setOverflow(false);
        //单页分页的条数限制，默认五限制
        pageInterceptor.setMaxLimit(500L);
        //设置数据库类型
        pageInterceptor.setDbType(DbType.MYSQL);

        interceptor.addInnerInterceptor(pageInterceptor);
        return interceptor;
    }
}
```

# 二、willDoQuery()方法

主要需要重写拦截器的两个方法，第一个是willDoQuery()。官方的例子中，在此方法执行了count()查询。如果查询结果条数为0，则`return false`不再执行原sql语句。

![image-20230305150719751](https://jack-image.oss-cn-shenzhen.aliyuncs.com/image/image-20230305150719751.png)

# 三、beforeQuery()方法

在执行原sql语句之前，先从参数中查找，看是否有分页参数`IPage`。如果存在分页参数，或者断言dialect，拼接分页查询sql。

```java
public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    // 是否存在分页参数
    IPage<?> page = ParameterUtils.findPage(parameter).orElse(null);
    if (null == page) {
        return;
    }

    // 处理 orderBy 拼接
    boolean addOrdered = false;
    String buildSql = boundSql.getSql();
    List<OrderItem> orders = page.orders();
    if (!CollectionUtils.isEmpty(orders)) {
        addOrdered = true;
        buildSql = this.concatOrderBy(buildSql, orders);
    }

    // size 小于 0 不构造分页sql
    if (page.getSize() < 0) {
        if (addOrdered) {
            PluginUtils.mpBoundSql(boundSql).sql(buildSql);
        }
        return;
    }

    handlerLimit(page);
    // 获取数据库断言
    IDialect dialect = findIDialect(executor);

    final Configuration configuration = ms.getConfiguration();
    // 根据断言，拼接分页sql
    DialectModel model = dialect.buildPaginationSql(buildSql, page.offset(), page.getSize());
    PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);

    List<ParameterMapping> mappings = mpBoundSql.parameterMappings();
    Map<String, Object> additionalParameter = mpBoundSql.additionalParameters();
    model.consumers(mappings, configuration, additionalParameter);
    mpBoundSql.sql(model.getDialectSql());
    mpBoundSql.parameterMappings(mappings);
}
```

至此，完成了分页查询操作。

总结：mybatis-plus的分页查询，是通过拦截器，在执行sql之前，检查参数中是否有分页参数，如果有，则根据数据库断言拼接分页查询sql。