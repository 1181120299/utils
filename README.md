# 前言
这是个人的整理的工具类，做到尽可能的开箱即用。主要有以下的功能：

# 一、redis缓存

（1）使用Spring Cache + redis作为缓存实现，可以为控制层（controller）接口增加redis缓存。使用也非常简单，只需要在控制层接口增加`@Cacheable`等注解。例如：

```java
private static final String BOOK_CACHE = "BOOK_CACHE";
private static final String KEY_GENERATOR = "simpleKeyGenerator";

@GetMapping("/info/{id}")
@Cacheable(value = BOOK_CACHE, keyGenerator = KEY_GENERATOR)
public R info(@PathVariable("id") Long id){
    // ...
}

@PostMapping("/update")
@CacheEvict(value = BOOK_CACHE, allEntries = true)
public R update(@RequestBody Book book){
    // ...
}
```

（2）可以通过配置项***`jack.redis.time-to-live`***配置缓存的过期时间（ttl），单位：秒。默认为60秒过期。配置示例：

```yaml
jack:
    redis:
        time-to-live: 30
```

（3）可以配合代码生成器`jack-generator`使用，将代码生成器的配置项***`useRedisCache`***设置为true，会在生成的控制器（controller）中增加缓存相关的注解。

# 二、通用的关联查询

用于两个实体类之间进行关联查询。使用示例请查看***`InSearch.fillDetail()`***方法的接口说明。

# 三、参数校验全局异常处理

（1）如果项目使用`hibernate-validator`校验前端输入参数，此工具类提供了参数校验异常的全局处理。参数校验失败返回信息示例：

```json
{
    "retCode": 2999,
    "retMsg": "用户名最多8个字符；密码不能超过6位",
    "data": {}
}
```

（2）搭配代码生成器`jack-generator`使用，使用代码生成器，会生成实体类对应的DTO。控制器（controller）中也会加上参数校验相关注解。

# 四、日期格式参数接收

可以直接使用`java.util.Date`对象接收前段传递的日期格式参数。

# 五、删除测试数据

项目测试阶段，正式上线前，难免需要清除测试数据。可以在数据库操作对象（Mapper）上使用注解***`DeleteTestData`***来删除数据。当配置项***`jack.mapper.delete-test-data`*** = true时，启动项目会执行数据清理操作。

# 六、定时任务类

通过**DynamicTask**添加、删除定时任务。使用示例请查看类注释说明。

# 七、excel打印格式优化

如果渲染的excel，要求不足一页数据时，要补全空行使打印出来更加美观。可以使用**ExcelHelper.addBlankRow()**方法增加空行。

# 八、日志输出

使用logbock日志框架，提供默认的日志输出策略。当**spring.profiles.active = prod**时，将**info**级别的日志输出到文件中。当**spring.profiles.active 不等于 prod或者没有配置时**，将**debug**级别的日志打印到控制台。日志文件路径为当前项目文件夹（或者jar包所在文件夹）下的logs文件夹里边。

# 













