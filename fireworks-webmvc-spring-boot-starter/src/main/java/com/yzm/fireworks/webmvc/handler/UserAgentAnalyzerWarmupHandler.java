package com.yzm.fireworks.webmvc.handler;

import com.google.common.base.Stopwatch;
import com.yzm.fireworks.webmvc.util.ClientInfoUtil;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UserAgentAnalyzer 启动预热组件。
 *
 * <p>Yauaa 首次初始化需要加载 classpath 中的 122 个 yaml 规则文件，耗时约 5~15 秒。
 * 若不预热，第一个触发 {@link ClientInfoUtil} 的业务请求会被完全阻塞。
 *
 * <p>本组件监听 {@link ApplicationReadyEvent}（Spring 容器完全就绪、
 * 对外接受请求之前的最后一个生命周期事件），提交到一个<b>自带的后台线程</b>中触发
 * {@link ClientInfoUtil#getAnalyzer()} 完成 yaml 加载，后续请求直接复用已就绪的单例。
 *
 * <p><b>为什么用自带线程而不是 {@code @Async}</b>：{@code @Async} 只有在业务应用标注了
 * {@code @EnableAsync} 时才生效，未标注就会被静默忽略、预热同步执行，导致
 * {@code ApplicationReadyEvent} 延后 5~15 秒（K8s readiness 探针会跟着晚就绪）。
 * 预热属于框架自身行为，不应依赖业务是否开启异步。
 *
 * <p>线程为 daemon，并在容器关闭时 {@code shutdownNow()}，不阻塞 JVM 退出。
 *
 * <p>此组件由 {@link com.yzm.fireworks.webmvc.WebMvcAutoConfiguration} 条件注册，
 * 仅在 {@code fireworks.webmvc.client-info.enabled=true} 时才加载。
 *
 * @author JYuan
 */
@Slf4j
public class UserAgentAnalyzerWarmupHandler {

    private final ExecutorService preheatExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "yauaa-warmup");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 应用就绪后提交预热任务，立即返回，不阻塞发布事件的线程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        preheatExecutor.execute(this::preheat);
    }

    private void preheat() {
        log.info("[Yauaa] 开始异步预热 UserAgentAnalyzer，加载 yaml 规则文件...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            // 触发 AnalyzerHolder 类加载并显式调用 preHeat 完成初始化
            ClientInfoUtil.getAnalyzer().preHeat();
            log.info("[Yauaa] UserAgentAnalyzer 预热完成，耗时 {}", stopwatch.stop());
        } catch (Exception e) {
            log.error("[Yauaa] UserAgentAnalyzer 预热失败", e);
        }
    }

    @PreDestroy
    public void destroy() {
        preheatExecutor.shutdownNow();
    }
}
