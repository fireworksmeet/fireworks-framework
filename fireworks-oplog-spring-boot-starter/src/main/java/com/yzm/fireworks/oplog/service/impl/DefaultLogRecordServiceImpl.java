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
        log.debug("logRecord:{}", logRecord);
    }
}
