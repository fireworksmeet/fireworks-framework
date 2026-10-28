package com.yzm.fireworks.oplog.service.impl;

import com.yzm.fireworks.oplog.beans.LogRecord;
import com.yzm.fireworks.oplog.service.ILogRecordService;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认日志保存实现
 * <p>
 * 仅打印日志，生产环境请实现 {@link ILogRecordService} 覆盖，
 * 对接内部数据库、MQ 或 ES。
 *
 * @author muzhantong
 */
@Slf4j
public class DefaultLogRecordServiceImpl implements ILogRecordService {

    @Override
    public void save(LogRecord logRecord) {
        // 只打定位所需字段：整对象 toString 会带出操作人姓名、action 业务文案与 extra，
        // DEBUG 一开就等于把业务数据写进日志文件
        log.debug("logRecord: type={}, bizNo={}, createdAt={}",
                logRecord.getType(), logRecord.getBizNo(), logRecord.getCreatedAt());
    }
}
