package com.yzm.fireworks.mybatisplus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis-Plus 粘合层配置。
 *
 * <pre>{@code
 * fireworks:
 *   mybatis-plus:
 *     pagination:
 *       max-limit: 500        # 单页最大条数；<=0 表示不限制
 *       db-type: postgresql   # 可选：强制指定方言；留空由 MyBatis-Plus 自动识别
 * }</pre>
 *
 * <p>方言默认不配置：MyBatis-Plus 在查询时按连接 URL 识别，多数据源下每个连接各自识别才是正确行为。
 * 只有在自动识别失效时（例如 URL 被 p6spy 之类代理包装）才需要显式指定——它是<b>全局覆盖</b>，
 * 多数据源场景请保持为空。
 *
 * @author JYuan
 */
@Data
@ConfigurationProperties(prefix = "fireworks.mybatis-plus")
public class MybatisPlusProperties {

    /**
     * 分页配置
     */
    private Pagination pagination = new Pagination();

    @Data
    public static class Pagination {

        /**
         * 单页最大条数限制，防止超大页拖垮数据库；留空或小于等于 0 表示不限制。
         */
        private Long maxLimit = 500L;

        /**
         * 强制指定分页方言，填 MyBatis-Plus DbType 的 db 名称（如 postgresql / mysql / oracle / sqlserver / dm / kingbase）。
         * 留空时由 MyBatis-Plus 按连接 URL 自动识别，这是默认且推荐的行为；
         * 仅当自动识别失效时（URL 被 p6spy 等代理包装）才需要填。它是全局覆盖，多数据源场景请保持为空。
         */
        private String dbType;
    }
}
