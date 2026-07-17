## 使用方式

> 1. 开启
>
>    ```yaml
>    leaf:
>      # 配置号段模式
>      segment:
>        enable: true
>        url: ${spring.datasource.url}
>        username: ${spring.datasource.username}
>        password: ${spring.datasource.password}
>    ```
>
> 2. 在数据库中执行`resources/db/T_LEAF_ALLOC.sql`，生成`T_LEAF_ALLOC`表
>
> 3. 在获取id前需要为当前号段在数据库插入一条数据
>
>    ```sql
>    insert into T_LEAF_ALLOC(biz_tag, max_id, step, description)
>    values ('自定义key', 首次获取的值, 一次取多少个, '为当前号段添加一个描述')
>
>    -- 如；初始值可以设置的大一点，假设订单号12位（参考京东），我们可以使用RandomUtils.nextLong(100000000000L, 200000000000L)获取一个12位的起始值然后插入其中
>    insert into T_LEAF_ALLOC(biz_tag, max_id, step, description)
>    values ('leaf-segment-test', 1, 5000, 'Test leaf Segment Mode Get Id')
>    ```
>
> 4. 然后调用`IdUtil.getId("上面插入时自定义key")`
>
> 5. <font color="gree">忽略日志</font>
>
>    > `Leaf` 的号段模式采用了双缓存（`Dual-Buffer`）异步加载的机制。
>    >
>    > 为了保证 `ID` 发放不间断，`Leaf` 在内存里存了两个号段（`Buffer`）。当一个号段快用完时，后台线程异步去数据库捞下一个号段。 为了防止缓存失效或检测数据库连接状态，`Leaf `源码中设计了一个定时任务（通常是 `ScheduledExecutorService`），默认**每 60 秒**就会强制检查或同步一次数据库。
>    >
>    > 同时，美团开源的 Leaf 源码（以及腾讯的衍生版）中集成了 **`Perf4j`**。这是一个性能监控工具，只要方法被执行，它就会雷打不动地以 `INFO` 级别输出耗时。
>    >
>    > <font color="orange">在生产环境中，这种每分钟刷屏一次的 INFO 日志确实挺烦人，会模糊掉真正关键的业务日志。可通过下面方式优雅地关掉它。</font>
>    >
>    > 如果你的项目使用的是`Logback`（`Spring Boot`默认），在`src/main/resources/logback-spring.xml`中添加以下两行配置
>    >
>    > ~~~xml
>    > <!-- 忽略 leaf 的日志 -->
>    > <logger name="com.tencent.devops.leaf.segment.SegmentIDGenImpl" level="WARN" />
>    > <logger name="org.perf4j.TimingLogger" level="WARN" />
>    > ~~~
>    >
>    > 如果你没有独立的 `XML` 配置文件，直接改 `Spring Boot` 的配置文件也能生效：
>    >
>    > ~~~yaml
>    > logging:
>    >   level:
>    >     com.tencent.devops.leaf.segment.SegmentIDGenImpl: WARN
>    >     org.perf4j.TimingLogger: WARN
>    > ~~~

