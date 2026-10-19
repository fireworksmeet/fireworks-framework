package com.yzm.fireworks.common.util;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.enums.DatabaseType;
import lombok.experimental.UtilityClass;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 数据库类型识别工具。
 * <p>
 * 只依赖 JDK 的 {@link DataSource} / {@link java.sql.DatabaseMetaData}，<b>不引入任何驱动或 ORM</b>。
 * 框架内需要按库分方言的组件统一走这里，避免各自写一套判定逻辑导致漂移。
 *
 * @author JYuan
 */
@UtilityClass
public class DatabaseUtil {

    private static final String PRODUCT_POSTGRES = "postgres";
    private static final String PRODUCT_MYSQL = "mysql";

    /**
     * 依据数据源识别数据库类型。
     *
     * @param dataSource 数据源
     * @return 数据库类型；不在内置支持矩阵内时返回 {@link DatabaseType#UNKNOWN}（由调用方决定 fail-fast 还是降级）
     * @throws IllegalStateException 无法从数据源获取连接或读取元数据
     */
    public static DatabaseType detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return ofProductName(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("读取数据库产品名失败，无法识别数据库类型", e);
        }
    }

    /**
     * 依据 JDBC 元数据的产品名判断数据库类型（纯函数，便于单测）。
     *
     * @param productName {@link java.sql.DatabaseMetaData#getDatabaseProductName()} 的返回值
     * @return 数据库类型，未匹配返回 {@link DatabaseType#UNKNOWN}
     */
    public static DatabaseType ofProductName(String productName) {
        String normalized = productName == null ? StringPool.EMPTY : productName.toLowerCase(Locale.ROOT);
        if (normalized.contains(PRODUCT_POSTGRES)) {
            return DatabaseType.POSTGRE_SQL;
        }
        if (normalized.contains(PRODUCT_MYSQL)) {
            return DatabaseType.MY_SQL;
        }
        return DatabaseType.UNKNOWN;
    }
}
