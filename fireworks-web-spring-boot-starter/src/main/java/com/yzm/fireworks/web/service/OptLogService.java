package com.yzm.fireworks.web.service;


import com.yzm.fireworks.web.context.OptLogContext;

/**
 * 操作日志处理服务接口
 *
 * <p>业务项目实现此接口，完成操作日志的持久化、推送等逻辑。
 *
 * <p><b>推荐实现方式：</b>
 * <pre>
 * {@code @Service}
 * public class SysOptLogServiceImpl implements OptLogService {
 *     {@code @Override}
 *     {@code @Async}  // 异步落库，不阻塞主请求线程
 *     public void handleLog(OptLogContext ctx) {
 *         // 1. 构建实体，2. 异步入库
 *     }
 * }
 * </pre>
 *
 * @author JYuan
 */
public interface OptLogService {

    /**
     * 处理操作日志
     *
     * <p>调用时机：目标方法执行完毕（含异常），在 finally 块中同步调用。
     * 实现方应通过 {@code @Async} 异步处理，避免影响主请求性能。
     *
     * @param context 已采集完毕的操作日志上下文
     */
    void handleLog(OptLogContext context);

}
