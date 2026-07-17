### 该starter提供了如下功能

### 1. `com.yzm.fireworks.redis`多数据源配置

#### 配置方式

> 当我们需要同时使用多个`com.yzm.fireworks.redis`数据源时，可进行如下配置
>
> ```yaml
> spring:
>   data:
>     com.yzm.fireworks.redis:
>       multi:
>         enable: true # 开启多数据源
>         primary: aaa # 配置默认数据源名称，没有显示指定使用哪个数据源时，默认使用该数据源
>         datasource:
>           aaa: # 自定义的数据源名称，下面就是RedisProperties的相关配置
>             host: 192.168.145.128
>             port: 6379  # 端口号，默认就是6379
>             timeout: 3000 # 连接超时时间（毫秒）
>           bbb: # 自定义的数据源名称，下面就是RedisProperties的相关配置
>             host: 192.168.145.129
>             port: 6379  # 端口号，默认就是6379
>             timeout: 3000 # 连接超时时间（毫秒）
> ```

#### 切换数据源

> 通过 `RedisUtil.on("数据源名称")` 显式切换数据源，返回绑定到该数据源的 `RedisUtil` 实例。
> 不调用 `on()` 则默认使用 primary 数据源。
>
> ```java
> @Autowired
> private RedisUtil redisUtil;
>
> // 使用 primary 数据源（默认）
> redisUtil.set("key", "value");
>
> // 切换到 bbb 数据源
> redisUtil.on("bbb").set("key", "value");
> ```
>
> <font color="red">注意：`on()` 是无状态的显式调用，不存在线程安全问题，在异步/多线程场景下同样适用。</font>

### 2. 分布式锁

> 当前框架中提供了`@DistributedLock`注解和`LockService`，两者都可以进行加锁。

#### 多数据源下的分布式锁

> 多数据源模式下，默认使用 primary 数据源的 `RedissonClient` 加锁。
> 可通过以下方式指定其他数据源：
>
> * <font color="orange">**注解形式**</font>
>
>   ```java
>   // 指定锁所在的 Redis 数据源
>   @DistributedLock(prefixKey = "order", key = "#orderId", datasource = "order")
>   public void processOrder(Long orderId) { ... }
>
>   // 不指定 datasource 则使用 primary 数据源
>   @DistributedLock(prefixKey = "order", key = "#orderId")
>   public void processOrder(Long orderId) { ... }
>   ```
>
> * <font color="orange">**编程形式**</font>
>
>   ```java
>   @Autowired
>   private LockService lockService;
>
>   // 使用 primary 数据源（默认）
>   lockService.executeWithLock("order:123", () -> doSomething());
>
>   // 切换到 order 数据源
>   lockService.on("order").executeWithLock("order:123", () -> doSomething());
>   ```

### 3. `JFR`记录`com.yzm.fireworks.redis`命令

> 可通过如下配置，在`jfr`中记录`com.yzm.fireworks.redis`命令的耗时情况
>
> ```yaml
> spring:
>   data:
>     com.yzm.fireworks.redis:
>       jfr:
>         enable: true
>         event-emit-interval: 10s # 表示每10秒内的命令做一次统计，框架中给定的默认值就是10s
> ```

### 4. `pipeline`使用的注意事项

> 为了保证全局都能正常使用`ObjectMapper`进行序列化与反序列化。使用`pipeline`方法时，不建议对值手动调用`getBytes()`方法（即使`value`的类型是字符串）。请按如下方式进行使用。
>
> ```java
> // 使用 JsonRedisTemplate，我更改了它底层的序列化方式
> JsonRedisTemplate redisTemplate = RedisUtil.getJsonRedisTemplate();
> String key = "key:111";
> String value = "value";
> // 获取它的值序列化器
> Jackson2JsonRedisSerializer<Object> valueSerializer = redisTemplate.getValueSerializer();
> 
> /*
>  * 还有一个重载方法
>  * executePipelined(RedisCallback<?> action, @Nullable RedisSerializer<?> resultSerializer)
>  * resultSerializer: 是用于返回值的，不要弄混了
>  */
> redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
>     // key推荐使用getBytes
>     byte[] keyBytes = key.getBytes();
>     /*
>      * 只要是value的都必须使用值序列化器
>      * 注意：
>      *     hash的value是最后一个，field依然算做key
>      *     zset的value是中间的那个，最后一个是score
>      */
>     byte[] serialize = valueSerializer.serialize(value);
>     connection.stringCommands().set(keyBytes, serialize);
>     return null;
> });
> ```
