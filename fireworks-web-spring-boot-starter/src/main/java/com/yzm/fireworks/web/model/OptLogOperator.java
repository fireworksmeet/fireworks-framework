package com.yzm.fireworks.web.model;

/**
 * 操作人信息接口
 *
 * <p>业务项目的 LoginUser 实现此接口后，{@link com.yzm.fireworks.web.aspectj.OptLogAspect}
 * 即可自动从 SecurityContext 中提取操作人 ID 和用户名，无需业务层手动传入。
 *
 * <p>示例：
 * <pre>
 * {@code @Data}
 * public class LoginUser implements OptLogOperator {
 *     private Long userId;
 *     private String username;
 *
 *     {@code @Override}
 *     public Long getOperatorId() { return userId; }
 *
 *     {@code @Override}
 *     public String getOperatorName() { return username; }
 * }
 * </pre>
 *
 * @author JYuan
 */
public interface OptLogOperator {

    /**
     * 操作人用户 ID
     */
    Long getOperatorId();

    /**
     * 操作人用户名（工号或昵称，用于日志展示）
     */
    String getOperatorName();
}
