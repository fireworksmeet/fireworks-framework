package com.yzm.fireworks.mybatisplus.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * 实体基类：主键 + 创建/更新时间。
 *
 * <p><b>自动填充由框架提供</b>：引入本 starter 后，框架注册的
 * {@code AuditMetaObjectHandler} 会在插入 / 更新时填充下列字段，
 * 业务<b>无需</b>再自行声明 {@code MetaObjectHandler}（自行声明后框架实现不再注册）。
 *
 * <p>填充前提：字段需声明 {@code @TableField(fill = ...)}（本类已声明），
 * 且当前值为 {@code null}。
 *
 * <p><b>动态切换数据源时注意</b>：自动填充发生在 MyBatis 执行阶段，取的是应用层时间，
 * 与数据库时区无关；时间类型统一用 {@link Instant}（绝对时间点）。
 *
 * <p><b>不要给流水 / 日志这类只插入不更新的表继承本类</b>：{@code updatedAt} 会永远等于
 * {@code createdAt}。自己声明 {@code id} + {@code createdAt} 即可，自动填充只认字段上的
 * {@code @TableField(fill = ...)}，与是否继承无关。
 *
 * <p><b>主键固定为数据库自增</b>（{@link IdType#AUTO}），会覆盖全局的 {@code id-type} 配置；
 * 需要用 CosId / 雪花发号时，不要继承本类，或在实体上重新声明 {@code id} 字段。
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间：插入时自动填充
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间：插入与更新时自动填充
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
