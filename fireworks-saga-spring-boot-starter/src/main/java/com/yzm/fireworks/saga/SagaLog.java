package com.yzm.fireworks.saga;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Saga日志实体
 *
 * @author JYuan
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SagaLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Saga全局事务ID
     */
    private String sagaId;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 状态
     */
    private SagaStatus status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

    /**
     * 下次重试时间
     */
    private Instant nextRetryTime;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 参数信息
     */
    private String param;

    /**
     * 创建时间
     * <p>
     * 由业务代码在插入前显式赋值，不依赖自动填充与数据库列默认值。
     */
    private Instant createdAt;

    /**
     * 更新时间
     * <p>
     * 由业务代码在插入/更新前显式赋值，不依赖自动填充与数据库列默认值。
     */
    private Instant updatedAt;
}
