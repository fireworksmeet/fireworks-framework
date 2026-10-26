package com.yzm.fireworks.outbox.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Outbox 事件状态。
 * <p>
 * 状态值以 {@code SMALLINT} 落库，编码固定（<b>禁止改数值</b>，否则存量数据语义漂移）：
 * <pre>
 * 10 PENDING    待投递（含等待重试）
 * 20 PROCESSING 已被某个 Worker 领取，正在执行
 * 30 SUCCEEDED  投递成功（终态）
 * 40 DEAD       重试耗尽或不可重试（终态，需人工介入后重置）
 * </pre>
 *
 * @author JYuan
 */
@Getter
@RequiredArgsConstructor
public enum OutboxEventStatus {

    /**
     * 待投递（含等待重试）
     */
    PENDING(10),

    /**
     * 已被 Worker 领取，正在执行
     */
    PROCESSING(20),

    /**
     * 投递成功
     */
    SUCCEEDED(30),

    /**
     * 重试耗尽或不可重试，需人工介入
     */
    DEAD(40);

    private final int code;
}
