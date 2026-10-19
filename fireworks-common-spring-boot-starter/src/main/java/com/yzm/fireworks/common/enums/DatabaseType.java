package com.yzm.fireworks.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 数据库类型（与 ORM、与任何具体持久化方式无关的公共枚举）。
 * <p>
 * 由 {@code DatabaseUtil.detect(DataSource)} 依据 JDBC 元数据识别，供框架内需要「按库分方言」的组件共用，
 * 例如 Outbox 的 {@code OutboxDialect} 选择、MyBatis-Plus 的 {@code DbType} 设置。
 * <p>
 * 命名与 MyBatis-Plus 的 {@code DbType} 保持一致，便于映射。
 *
 * @author JYuan
 */
@Getter
@RequiredArgsConstructor
public enum DatabaseType {

    /**
     * PostgreSQL
     */
    POSTGRE_SQL("PostgreSQL"),

    /**
     * MySQL
     */
    MY_SQL("MySQL"),

    /**
     * 未识别（框架内置支持矩阵之外）
     */
    UNKNOWN("未知");

    private final String description;

    /**
     * 是否为框架内置支持的数据库。
     */
    public boolean isSupported() {
        return this != UNKNOWN;
    }
}
