package com.yzm.fireworks.web.spi;


import com.yzm.fireworks.web.model.OptLogOperator;

/**
 * 操作人信息提供者
 *
 * <p>框架层定义此接口，业务层提供实现，{@link com.yzm.fireworks.web.aspectj.OptLogAspect}
 * 在运行时通过 Spring 容器懒加载，找不到实现则跳过操作人信息采集。
 *
 * <p>这样框架层与 Spring Security 完全解耦——使用 Security 的项目注册一个实现，
 * 不使用 Security 的项目可提供自己的实现，或不注册（操作人字段留空）。
 *
 * <p><b>使用 Spring Security 的项目示例：</b>
 * <pre>
 * {@code @Component}
 * public class SecurityOptLogOperatorProvider implements OptLogOperatorProvider {
 *     {@code @Override}
 *     public OptLogOperator currentOperator() {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth != null {@code &&} auth.getPrincipal() instanceof OptLogOperator op) {
 *             return op;
 *         }
 *         return null;
 *     }
 * }
 * </pre>
 *
 * <p><b>不使用 Security 的项目示例（如 ThreadLocal 方案）：</b>
 * <pre>
 * {@code @Component}
 * public class ThreadLocalOptLogOperatorProvider implements OptLogOperatorProvider {
 *     {@code @Override}
 *     public OptLogOperator currentOperator() {
 *         return UserContext.current(); // 自定义 ThreadLocal
 *     }
 * }
 * </pre>
 *
 * @author JYuan
 */
public interface OptLogOperatorProvider {

    /**
     * 获取当前操作人信息
     *
     * @return 当前操作人，未登录或无法获取时返回 {@code null}
     */
    OptLogOperator currentOperator();
}
