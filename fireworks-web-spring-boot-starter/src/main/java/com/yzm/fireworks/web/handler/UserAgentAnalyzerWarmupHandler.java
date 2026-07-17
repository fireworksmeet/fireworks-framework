package com.yzm.fireworks.web.handler;

import com.google.common.base.Stopwatch;
import com.yzm.fireworks.web.util.ClientInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * UserAgentAnalyzer 启动预热组件。
 *
 * <p>Yauaa 首次初始化需要加载 classpath 中的 122 个 yaml 规则文件，耗时约 5~15 秒。
 * 若不预热，第一个触发 {@link ClientInfoUtil} 的业务请求会被完全阻塞。
 *
 * <p>本组件监听 {@link ApplicationReadyEvent}（Spring 容器完全就绪、
 * 对外接受请求之前的最后一个生命周期事件），在独立线程中提前触发
 * {@link ClientInfoUtil#getAnalyzer()} 完成 yaml 加载，
 * 后续所有请求直接复用已就绪的单例，不再有任何阻塞。
 *
 * <p><b>为什么用 {@code ApplicationReadyEvent} 而非 {@code @PostConstruct}？</b><br>
 * {@code @PostConstruct} 在 Bean 初始化阶段同步执行，若此处触发耗时操作会拖慢整个
 * Spring 容器启动。{@code ApplicationReadyEvent} 在容器完全启动后才发布，
 * 配合 {@code @Async} 可在后台线程并行预热，不阻塞主线程。
 *
 * <p>此组件由 {@link com.yzm.fireworks.web.WebAutoConfiguration} 条件注册，
 * 仅在 {@code fireworks.web.client-info.enabled=true} 时才加载。
 *
 * @author JYuan
 */
@Slf4j
public class UserAgentAnalyzerWarmupHandler {

    /**
     * 应用就绪后异步预热 UserAgentAnalyzer。
     *
     * <p>{@code @Async} 需要在任意 {@code @Configuration} 类上标注
     * {@code @EnableAsync} 方可生效；若项目已开启则直接生效，
     * 否则预热方法会在发布事件的线程上同步执行（仍在启动末尾，不影响业务请求，
     * 但会稍微延长启动日志中 "Started XxxApplication" 的出现时机）。
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
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
}