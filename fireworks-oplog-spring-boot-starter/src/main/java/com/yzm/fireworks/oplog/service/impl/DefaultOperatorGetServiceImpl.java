package com.yzm.fireworks.oplog.service.impl;

import com.yzm.fireworks.oplog.beans.Operator;
import com.yzm.fireworks.oplog.service.IOperatorGetService;

/**
 * 默认操作人获取实现
 * <p>
 * 不提供任何兜底值：操作人属于审计信息，写入 {@code 0/system} 这类假数据
 * 会静默污染日志，比直接失败更危险。因此本实现在被调用时直接抛出异常，
 * 提示业务方配置操作人来源。
 * <p>
 * 本实现仅在「{@code @LogRecord} 未指定 {@code operator} 表达式，
 * 且业务方未自行实现 {@link IOperatorGetService}}」时才被调用，
 * 因此<b>不会影响</b>以下两种正确用法：
 * <ul>
 *     <li>注解指定了 {@code operator}：直接采用表达式结果，不调用本服务</li>
 *     <li>业务实现了 {@link IOperatorGetService}：走业务实现，本类不生效</li>
 * </ul>
 *
 * @author muzhantong
 */
public class DefaultOperatorGetServiceImpl implements IOperatorGetService {

    @Override
    public Operator getUser() {
        throw new UnsupportedOperationException(
                "未配置操作人来源：请在 @LogRecord 中指定 operator 表达式"
                        + "（如 operator = \"#{T(com.xxx.UserContext).getUser()}\"），"
                        + "或实现 IOperatorGetService 返回当前登录用户");
    }
}
