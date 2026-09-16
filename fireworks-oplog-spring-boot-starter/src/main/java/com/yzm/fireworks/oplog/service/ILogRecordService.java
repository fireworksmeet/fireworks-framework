package com.yzm.fireworks.oplog.service;

import com.yzm.fireworks.oplog.beans.LogRecord;

/**
 * 操作日志保存服务（SPI）
 * <p>
 * 框架只负责「生产」日志——拦截方法、解析注解、渲染 SpEL 模板、组装日志实体；
 * 业务方实现本接口完成「存储」，可写入数据库、MQ、ES 或日志文件。
 * <p>
 * <b>为什么没有查询方法</b>：日志的查询、展示、导出等属于「消费」场景，
 * 由业务层自行实现。原因在于查询模型与写入模型通常不同：
 * <ul>
 *     <li>{@link LogRecord} 是写入模型，含 {@code codeVariable} 等仅写入需要的字段</li>
 *     <li>查询通常需要分页、总数、时间范围、脱敏、高亮等，需要独立的 VO</li>
 * </ul>
 * 因此框架不定义查询契约，避免业务被迫实现无意义的空方法。
 *
 * @author JYuan
 */
public interface ILogRecordService {

    /**
     * 保存一条操作日志
     * <p>
     * <b>实现建议</b>：
     * <ul>
     *     <li><b>异步执行</b>：使用 {@code @Async} 或投递 MQ，避免阻塞业务主流程</li>
     *     <li><b>异常兜底</b>：保存失败时记录日志即可，不要向调用方抛出异常。
     *         框架层已做兜底，但实现方仍应保证自身健壮性</li>
     * </ul>
     *
     * @param logRecord 日志记录
     */
    void save(LogRecord logRecord);
}
