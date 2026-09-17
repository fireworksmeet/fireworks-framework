package com.yzm.fireworks.web.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.Instant;

/**
 * 实体基类，提供主键与创建/更新时间。
 * <p>
 * <b>关于自动填充（重要）</b>：{@code createdAt} / {@code updatedAt} 声明了
 * {@link FieldFill} 填充策略，但该策略<b>不会自动生效</b>——MyBatis-Plus 要求容器中存在
 * {@code MetaObjectHandler} 实现才会执行填充。因此使用本基类的业务需二选一：
 * <ul>
 *     <li>注册 {@code MetaObjectHandler} 实现自动填充（推荐，可统一处理时间与操作人）</li>
 *     <li>自行在插入前赋值；或为对应数据库列设置 {@code DEFAULT CURRENT_TIMESTAMP}</li>
 * </ul>
 * 未做上述任一项时，字段会以 {@code null} 写入，<b>不会有任何报错</b>。
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间
     * <p>
     * 依赖 {@code MetaObjectHandler} 填充，详见类注释。
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     * <p>
     * 依赖 {@code MetaObjectHandler} 填充，详见类注释。
     * 注意：数据库列默认值只在插入时生效，更新时需由填充策略或业务代码负责。
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}