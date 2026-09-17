package com.yzm.fireworks.web.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 带有人员审计的实体基类（适合 B 端管理表）。
 * <p>
 * <b>必须注册 MetaObjectHandler（重要）</b>：{@code createdBy} / {@code updatedBy}
 * 依赖 {@code MetaObjectHandler} 填充。与 {@link BaseEntity} 的时间字段不同，
 * <b>人员信息无法通过数据库列默认值兜底</b>——数据库不知道当前登录人是谁。
 * <p>
 * 若继承本基类却未注册 {@code MetaObjectHandler}，这两个字段会<b>静默写入 {@code null}</b>，
 * 表现为审计信息丢失，且不会有任何报错或告警，问题往往在事后追溯时才被发现。
 * <p>
 * 正确用法示例：
 *
 * <pre>{@code
 * @Component
 * public class AuditMetaObjectHandler implements MetaObjectHandler {
 *
 *     @Override
 *     public void insertFill(MetaObject metaObject) {
 *         Instant now = Instant.now();
 *         // 只对声明了 fill 策略的字段生效，未声明的字段会被自动跳过
 *         strictInsertFill(metaObject, "createdAt", Instant.class, now);
 *         strictInsertFill(metaObject, "updatedAt", Instant.class, now);
 *         Long userId = CurrentUser.getUserId();
 *         strictInsertFill(metaObject, "createdBy", Long.class, userId);
 *         strictInsertFill(metaObject, "updatedBy", Long.class, userId);
 *     }
 *
 *     @Override
 *     public void updateFill(MetaObject metaObject) {
 *         strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
 *         strictUpdateFill(metaObject, "updatedBy", Long.class, CurrentUser.getUserId());
 *     }
 * }
 * }</pre>
 *
 * @author JYuan
 * @see BaseEntity 关于自动填充机制的说明
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class BaseOperatorEntity extends BaseEntity {

    /**
     * 创建人
     * <p>
     * 依赖 {@code MetaObjectHandler} 填充，无数据库默认值可兜底，详见类注释。
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新人
     * <p>
     * 依赖 {@code MetaObjectHandler} 填充，无数据库默认值可兜底，详见类注释。
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}