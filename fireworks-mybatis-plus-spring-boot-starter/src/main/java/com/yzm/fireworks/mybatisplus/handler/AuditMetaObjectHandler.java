package com.yzm.fireworks.mybatisplus.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yzm.fireworks.mybatisplus.support.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;

/**
 * 审计字段自动填充。
 *
 * <p>填充策略（仅对<b>声明了 {@code @TableField(fill = ...)} 的字段</b>生效）：
 * <ul>
 *   <li>{@code createdAt} / {@code updatedAt}：<b>总是填充</b>（插入 / 更新时各填一次）；</li>
 *   <li>{@code createdBy} / {@code updatedBy}：<b>能解析到登录人时才填充</b>。</li>
 * </ul>
 *
 * <p><b>为什么审计人不写默认值</b>：定时任务、MQ 消费等场景没有登录上下文，若强行写入 {@code null}
 * 或某个占位 ID，会造成「审计信息静默错误」。因此这里只在 {@link CurrentUserProvider}
 * 能返回真实用户时填充，否则留空由业务自行决定。
 *
 * @author JYuan
 */
@RequiredArgsConstructor
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final ObjectProvider<CurrentUserProvider> currentUserProvider;

    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = Instant.now();
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);

        Long userId = currentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, "createdBy", Long.class, userId);
            strictInsertFill(metaObject, "updatedBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());

        Long userId = currentUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }

    /**
     * 解析当前登录人；无 provider（未引入鉴权体系）时返回 {@code null}。
     */
    private Long currentUserId() {
        CurrentUserProvider provider = currentUserProvider.getIfAvailable();
        return provider == null ? null : provider.getUserId();
    }
}
