- [ ] 启动类是如何将程序运行起来的？
- [ ] starter实现自动注入的原理？



```java
@SpringBootApplication
public class ValidatecodeApplication {

   public static void main(String[] args) {
      SpringApplication.run(ValidatecodeApplication.class, args);
   }

}
```

启动类上需要加@SpringBootApplication注解，那么这个注解的作用是？首先来看这个注解的定义：

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { @Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
      @Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class) })
public @interface SpringBootApplication {
    //...
}
```

@SpringBootApplication标注的类，表明这是一个**配置类**（@Configuration），因此可以在里边定义@Bean。另外，还会触发**自动装配**（auto-configuration）和**组件扫描**（component scanning）。

主要来看下两个注解：@SpringBootConfiguration和@EnableAutoConfiguration

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration
@Indexed
public @interface SpringBootConfiguration {
	@AliasFor(annotation = Configuration.class)
	boolean proxyBeanMethods() default true;
}
```

上面标注了@Configuration，唯一的方法声明，也和@Configuration中的proxyBeanMethods（）方法一致，猜测@SpringBootConfiguration注解的作用和@Configuration应该是一样的。

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration {

	String ENABLED_OVERRIDE_PROPERTY = "spring.boot.enableautoconfiguration";

	Class<?>[] exclude() default {};

	String[] excludeName() default {};

}
```

@EnableAutoConfiguration注解，会开启自动装配，根据classpath路径上有的jar包，猜测你需要的bean，进行组装。注解定义了两个用于排除不需要装配的类的方法。主要来看下两个注解：@AutoConfigurationPackage和@Import(AutoConfigurationImportSelector.class)

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(AutoConfigurationPackages.Registrar.class)
public @interface AutoConfigurationPackage {

   String[] basePackages() default {};

   Class<?>[] basePackageClasses() default {};

}
```

@AutoConfigurationPackage主要是注册要扫描的基础包（basePackage）。需要注意的是@Import(AutoConfigurationPackages.Registrar.class)，猜测可以按包进行自动装配并放入Spring容器。

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Import {

   /**
    * {@link Configuration @Configuration}, {@link ImportSelector},
    * {@link ImportBeanDefinitionRegistrar}, or regular component classes to import.
    */
   Class<?>[] value();

}
```

@Import注解用来导入一个或者多个组件（component）。从定义的value()方法可知，主要有四种用法：

- 导入@Configuration标注的类，配置类中定义的@Bean都会导入到Spring容器中
- **实现了ImportSelector接口的类，会寻找资源目录下的META-INF/spring.factories文件，将文件中定义的类进行自动装配并导入。这也是推荐的用法，用来自定义starter实现无侵入性。**
- 实现了ImportBeanDefinitionRegistrar接口的类，可以注册类定义（bean definition）。例如AutoConfigurationPackages.Registrar就可以扫描包下的类进行自动装配并导入。
- 其它组件，主要是指导入个人自定义的类

说了这个多理论的东西，接下来就开发个公共模块，作为starter供其它项目引用。

