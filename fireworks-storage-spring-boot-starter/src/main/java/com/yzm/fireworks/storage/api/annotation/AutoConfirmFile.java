package com.yzm.fireworks.storage.api.annotation;

import com.yzm.fireworks.storage.core.orphan.OrphanFileGuard;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式自动确认文件：标注在 Service 保存方法上，方法执行成功后自动调用
 * {@link OrphanFileGuard#confirm} 批量确认孤儿文件记录，业务方无需手写 confirm 代码。
 * <p>
 * 适用场景：表单保存（如用户资料、商品、评论）时，前端直传完成后把文件对象名作为
 * {@code String}（或 {@code List<String>}）传给后端持久化到业务表。标注本注解后，框架会从<b>方法参数</b>
 * 中按 {@link #objectKey()} 指定的 SpEL 表达式精确提取这些对象名，在<b>事务提交之后</b>自动确认，
 * 从根本上避免业务开发漏写 {@code confirm} 导致已正常使用的文件被误判为孤儿而清理。
 * <p>
 * 用法示例：
 * <pre>{@code
 * // 单个文件对象名在入参 DTO 上（最常见）
 * @AutoConfirmFile(objectKey = "#userForm.avatarPath")
 * public User save(UserForm userForm) { ... }
 *
 * // 批量：一组文件对象名（List<String>）
 * @AutoConfirmFile(objectKey = "#args[0]")
 * public void savePics(List<String> picUrls) { ... }
 *
 * // 显式指定桶名（SpEL 或回退到配置 default-bucket）
 * @AutoConfirmFile(bucket = "#userForm.bucket", objectKey = "#userForm.avatarPath")
 * public void save(UserForm form) { ... }
 * }</pre>
 * <p>
 * SpEL 变量：方法参数名（如 {@code #userForm}）、{@code #args}（参数数组，按 {@code #args[0]} 位置访问）、
 * 以及 {@code #p0}/{@code #a0} 等索引别名。文件路径统一通过参数获取，不解析方法返回值。
 * <p>
 * 说明：{@link #objectKey()} 为必填，用 SpEL 精确定位文件路径，避免盲目扫描造成误确认。
 * SpEL 解析结果需为 {@code String} 或 {@code String} 的集合/数组，框架会展开为待确认记录并批量确认。
 * <p>
 * 事务行为：自动感知事务。方法在事务中时，等事务提交（{@code afterCommit}）后确认，保证事务回滚时不误确认；
 * 方法不在事务中时，则方法执行成功后立即确认。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoConfirmFile {

    /**
     * 桶名 SpEL 表达式（可选）。为空时回退到配置 {@code fireworks.storage.orphan-cleanup.default-bucket}；
     * 仍无法确定则跳过（不确认）。
     */
    String bucket() default "";

    /**
     * 对象名 SpEL 表达式（必填）。通过 SpEL 从方法参数中精确定位文件对象名。
     * <p>
     * 例如参数名 {@code "#userForm.avatarPath"}、位置访问 {@code "#args[0]"}。
     * 解析结果需为 {@code String} 或 {@code String} 的集合/数组。
     */
    String objectKey() default "";
}
