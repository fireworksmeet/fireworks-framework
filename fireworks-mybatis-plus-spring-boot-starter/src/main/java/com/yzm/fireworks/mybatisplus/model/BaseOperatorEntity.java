package com.yzm.fireworks.mybatisplus.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

/**
 * 带人员审计的实体基类（适合 B 端管理表）。
 *
 * <p><b>填充规则</b>：{@code createdBy} / {@code updatedBy} 由框架的
 * {@code AuditMetaObjectHandler} 填充，<b>仅当能解析到当前登录人时</b>：
 * <ul>
 *   <li>声明了 {@code CurrentUserProvider} Bean：按它的实现解析登录人（用 security 时返回
 *       {@code SecurityUserContext::getUserId} 即可）；</li>
 *   <li>未声明，或定时任务 / MQ 消费等<b>无登录上下文</b>的场景：<b>不填充</b>，
 *       字段保持 {@code null}——这是有意为之，避免写入错误的占位人。</li>
 * </ul>
 * 上述「无登录人」场景若需要审计，请在业务代码里显式赋值。
 *
 * <p><b>这里只存用户 id，不存姓名</b>：姓名会变（改名、离职），存快照会让同一列新旧混杂。
 * 展示时按 id 批量补全；只有订单、合同这类凭证表才需要姓名快照，那时由业务自己加列。
 *
 * @author JYuan
 * @see BaseEntity 时间字段的填充规则
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class BaseOperatorEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建人：插入时填充（能解析到登录人时）
     */
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 更新人：插入与更新时填充（能解析到登录人时）
     */
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;
}
