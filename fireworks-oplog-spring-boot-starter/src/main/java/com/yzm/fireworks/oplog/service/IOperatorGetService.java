package com.yzm.fireworks.oplog.service;

import com.yzm.fireworks.oplog.beans.Operator;

/**
 * 操作人获取服务
 * <p>
 * 业务方实现此接口，从内部鉴权体系（如 Sa-Token、Spring Security、
 * 自研 Token）中获取当前操作人。
 * <p>
 * <b>实现要求</b>：应同时返回操作人 ID 与姓名快照。姓名会随日志一并落库，
 * 用户后续改名或删除都不会影响历史日志的审计准确性。
 *
 * @author JYuan
 */
public interface IOperatorGetService {

    /**
     * 获取当前操作人
     *
     * @return 操作人（含 ID 与姓名快照）
     */
    Operator getUser();
}
