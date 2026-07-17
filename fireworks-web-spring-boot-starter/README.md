### 该starter提供了如下功能

### 1. 日志

#### 系统日志

> 当前框架提供了系统日志功能，可通过`@EnableSystemLog`注解进行开启。
>
> 开启后，每个请求的参数、耗时、返回值都会在日志中进行记录

#### 操作日志

> 1. 当前框架还提供了操作日志功能，可用户记录用户的行为。需要通过`@EnableSystemLog`注解进行开启。
> 2. 开启后，每个标记了`@OptLog`注解的方法，都会触发`OptLogService`的`processLog`方法，程序员可自定义该方法的实现类

### 2. 字典表

> 在`resources`目录下`dict_table.sql`文件中存放了常用的2张字典表

### 3. 排序组件的使用

> 项目中提供了通用的排序组件，可通过如下方式在业务中使用。
>
> 1. 定义一个枚举类，用于和前端通信。前端不传时，后端指定一个默认值。前端传递时以前端为准。
>
>    ~~~java
>    package com.yzm.fireworks.api.common.enums.sort;
>    
>    import com.baomidou.mybatisplus.annotation.IEnum;
>    import com.fasterxml.jackson.annotation.JsonCreator;
>    import com.fasterxml.jackson.annotation.JsonProperty;
>    import com.fasterxml.jackson.annotation.JsonValue;
>    import com.yzm.fireworks.api.util.EnumUtil;
>    import io.swagger.v3.oas.annotations.media.Schema;
>    import lombok.AllArgsConstructor;
>    import lombok.Getter;
>    
>    /**
>     * 相亲卡片排序类型
>     *
>     * @author JYuan
>     */
>    @Getter
>    @AllArgsConstructor
>    @Schema(description = "排序类型 (0:通用的最新加入,1:通用的注册最久)")
>    public enum SortType implements IEnum<Integer> {
>        /**
>         * 最新加入
>         */
>        COMMON_LATEST(0),
>    
>        /**
>         * 注册最久（最长辈）
>         */
>        COMMON_OLDEST(1),
>    
>        /**
>         * 操作日志最新加入
>         */
>        OPT_LOG_LATEST(2),
>    
>        /**
>         * 操作日志注册最久（最长辈）
>         */
>        OPT_LOG_OLDEST(3),
>    
>        /**
>         * 登录日志最新加入
>         */
>        LOGIN_LOG_LATEST(4),
>    
>        /**
>         * 登录日志注册最久（最长辈）
>         */
>        LOGIN_LOG_OLDEST(5),
>    
>        ;
>    
>        @JsonValue
>        private final Integer value;
>    
>        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
>        public static SortType get(@JsonProperty("value") Integer value) {
>            return EnumUtil.match(SortType.class, value);
>        }
>    }
>    ~~~
>
> 2. 定义一个排序注册器实现`AbstractSortRegistry`组件
>
>    ~~~java
>    package com.yzm.fireworks.biz.sort;
>    
>    import com.yzm.fireworks.api.common.enums.sort.SortType;
>    import com.yzm.fireworks.biz.entity.system.SysOptLog;
>    import com.yzm.fireworks.web.model.BaseEntity;
>    import com.yzm.fireworks.web.sort.AbstractSortRegistry;
>    import com.yzm.fireworks.web.sort.SortBuilder;
>    import org.springframework.stereotype.Component;
>    
>    @Component
>    public class DefaultSortRegistry extends AbstractSortRegistry<SortType> {
>    
>        public DefaultSortRegistry() {
>            super(SortType.class);
>        }
>    
>        @Override
>        protected void register() {
>            // 1. 最新加入 -> 对应创建时间降序
>            register(SortType.COMMON_LATEST, new SortBuilder()
>                    .desc(BaseEntity::getCreatedAt)
>                    // id是保稳用的，避免多个时间相同的情况
>                    .desc(BaseEntity::getId)
>                    .build());
>    
>            // 2. 注册最久 -> 对应创建时间升序
>            register(SortType.COMMON_OLDEST, new SortBuilder()
>                    .asc(BaseEntity::getCreatedAt)
>                    .asc(BaseEntity::getId)
>                    .build());
>    
>            // 3. 操作日志最新加入 -> 对应操作时间降序
>            register(SortType.OPT_LOG_LATEST, new SortBuilder()
>                    .desc(SysOptLog::getOperateTime)
>                    .desc(BaseEntity::getId)
>                    .build());
>    
>            // 4. 操作日志注册最久 -> 对应操作时间升序
>            register(SortType.OPT_LOG_OLDEST, new SortBuilder()
>                    .asc(SysOptLog::getOperateTime)
>                    .asc(BaseEntity::getId)
>                    .build());
>        }
>    }
>    ~~~
>
> 3. 在需要排序的地方引入上方实现的注册器，然后调用`apply`方法，将排序字段塞入`page`对象中。
>
>    ~~~java
>    package com.yzm.fireworks.auth.service.impl;
>    
>    import com.baomidou.mybatisplus.core.metadata.IPage;
>    import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
>    import com.yzm.fireworks.auth.entity.SysLoginLog;
>    import com.yzm.fireworks.auth.mapper.SysLoginLogMapper;
>    import com.yzm.fireworks.auth.model.bo.SysLoginLogBO;
>    import com.yzm.fireworks.auth.model.bo.SysLoginLogQueryBO;
>    import com.yzm.fireworks.auth.model.convert.SysLoginLogConvert;
>    import com.yzm.fireworks.auth.service.ISysLoginLogService;
>    import com.yzm.fireworks.auth.sort.DefaultSortRegistry;
>    import lombok.RequiredArgsConstructor;
>    import org.springframework.stereotype.Service;
>    
>    /**
>     * 登录日志服务实现
>     *
>     * @author JYuan
>     */
>    @Service
>    @RequiredArgsConstructor
>    public class SysLoginLogServiceImpl extends ServiceImpl<SysLoginLogMapper, SysLoginLog>
>            implements ISysLoginLogService {
>    
>        private final SysLoginLogMapper sysLoginLogMapper;
>        private final SysLoginLogConvert sysLoginLogConvert;
>        private final DefaultSortRegistry defaultSortRegistry;
>    
>        @Override
>        public IPage<SysLoginLogBO> pageLoginLogs(SysLoginLogQueryBO query) {
>            IPage<SysLoginLog> page = query.createPage();
>          
>            // 根据枚举塞入对应的排序字段
>            defaultSortRegistry.apply(query.getSortType(), page);
>            IPage<SysLoginLog> entityPage = sysLoginLogMapper.selectLoginLogPage(page, query);
>            return entityPage.convert(sysLoginLogConvert::convertBO);
>        }
>    }
>    ~~~

