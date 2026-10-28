package com.yzm.fireworks.mybatisplus;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yzm.fireworks.mybatisplus.handler.AuditMetaObjectHandler;
import com.yzm.fireworks.mybatisplus.properties.MybatisPlusProperties;
import com.yzm.fireworks.mybatisplus.support.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 粘合层自动配置。
 *
 * <p>本模块<b>不是</b>官方 starter 的再包装，只做官方 starter 不做、而每个项目都要做的两件事：
 * <ol>
 *   <li><b>分页拦截器自动装配</b>：官方 starter 不会自动注册 {@code MybatisPlusInterceptor}（最常见的踩坑点），
 *       这里注册并设置单页上限；{@link DbType} 默认不指定，由 MyBatis-Plus 在查询时按连接 URL 识别
 *       （多数据源下每个连接各自识别），仅在自动识别失效时用 {@code pagination.db-type} 显式覆盖；</li>
 *   <li><b>审计字段自动填充</b>：注册 {@link AuditMetaObjectHandler}。</li>
 * </ol>
 *
 * <p><b>刻意不内置</b>乐观锁、防全表更新/删除等插件——需要时通过 {@link MybatisPlusCustomizer} 追加
 * （这样不会覆盖框架装配的分页拦截器）。
 *
 * <p>所有 Bean 都带 {@link ConditionalOnMissingBean}，业务可整体接管。
 *
 * <p><b>类名带 {@code Fireworks} 前缀是故意的</b>：MyBatis-Plus 自身存在
 * {@code com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration}
 * （3.5.6 等版本），同名会让日志与异常栈难以分辨。
 *
 * @author JYuan
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(MybatisPlusProperties.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
@ConditionalOnSingleCandidate(DataSource.class)
public class FireworksMybatisPlusAutoConfiguration {

    /**
     * 装配「分页」拦截器，并依次应用业务自定义的 {@link MybatisPlusCustomizer}。
     * <p>若业务自行声明了 {@code MybatisPlusInterceptor}，本 Bean 让位（此时需自行保证分页已配置）。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(MybatisPlusProperties properties,
                                                        ObjectProvider<MybatisPlusCustomizer> customizers) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(paginationInnerInterceptor(properties));
        customizers.orderedStream().forEach(customizer -> customizer.customize(interceptor));
        return interceptor;
    }

    /**
     * 审计字段填充器。
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler auditMetaObjectHandler(ObjectProvider<CurrentUserProvider> currentUserProvider) {
        return new AuditMetaObjectHandler(currentUserProvider);
    }

    private PaginationInnerInterceptor paginationInnerInterceptor(MybatisPlusProperties properties) {
        MybatisPlusProperties.Pagination pagination = properties.getPagination();
        // 未配置时传 null：MP 在查询时按连接 URL 识别方言（多数据源下每个连接各自识别）
        DbType dbType = resolveDbType(pagination.getDbType());
        PaginationInnerInterceptor inner = new PaginationInnerInterceptor(dbType);

        Long maxLimit = pagination.getMaxLimit();
        inner.setMaxLimit(maxLimit != null && maxLimit > 0 ? maxLimit : null);

        log.info("[MyBatis-Plus] 分页拦截器已装配：db-type={}, max-limit={}",
                dbType == null ? "auto" : dbType.getDb(), maxLimit);
        return inner;
    }

    /**
     * 把配置值解析为 MyBatis-Plus 的 {@link DbType}；留空返回 {@code null}（交给 MP 自动识别）。
     * <p>用 {@link DbType#getDbType(String)} 而非枚举绑定：配置按 MP 的 db 名称书写（{@code postgresql}），
     * 而枚举常量叫 {@code POSTGRE_SQL}，直接绑定会要求写成 {@code postgre-sql}。
     */
    private DbType resolveDbType(String configured) {
        if (!StringUtils.hasText(configured)) {
            return null;
        }
        DbType dbType = DbType.getDbType(configured.strip());
        if (dbType == null) {
            throw new IllegalStateException("[MyBatis-Plus] 不支持的分页方言：fireworks.mybatis-plus.pagination.db-type="
                    + configured + "；取值见 com.baomidou.mybatisplus.annotation.DbType"
                    + "（如 postgresql / mysql / oracle / sqlserver / dm / kingbase），留空则自动识别");
        }
        return dbType;
    }
}
