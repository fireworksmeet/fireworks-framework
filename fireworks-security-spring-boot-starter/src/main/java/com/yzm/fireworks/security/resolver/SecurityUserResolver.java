package com.yzm.fireworks.security.resolver;

import com.yzm.fireworks.security.context.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 身份解析扩展点（SPI）：业务只回答「这个请求是谁」。
 *
 * <p>框架负责其余全部工作——按 {@code role-prefix} 组装 authorities、写入
 * {@code SecurityContext}、请求结束清理、输出 401 / 403 / 500。业务实现里
 * <b>不要</b>操作 {@code SecurityContextHolder}。
 *
 * <pre>{@code
 * @Component
 * public class GatewayUserResolver implements SecurityUserResolver {
 *     @Override
 *     public SecurityUser resolve(HttpServletRequest request) {
 *         String header = request.getHeader("X-User-Context");
 *         if (!StringUtils.hasText(header)) {
 *             return null;                       // 无凭证 → 401
 *         }
 *         return JsonUtil.deserialize(Base64Util.decodeUrl(header), SecurityUser.class);
 *     }
 * }
 * }</pre>
 *
 * <p>【契约】
 * <ol>
 *   <li><b>无凭证 / 凭证不可用</b>（未带 Token、签名不合法、已过期）→ 返回 {@code null}，
 *       框架按未登录处理（401）。这是常态，不要抛异常。</li>
 *   <li><b>解析过程出错</b>（凭证格式错误、验签服务不可用、远端 introspection 超时）→ 直接抛出：
 *       框架记 ERROR + 堆栈，并直接返回 <b>500</b>（fail-closed），不再继续过滤链。
 *       框架不判断异常类型，也就无法区分“凭证问题”和“依赖故障”——凭证问题请按上一条返回 {@code null}。</li>
 *   <li>返回 {@code null} 表示<b>未认证</b>（401）；返回非 {@code null} 即视为<b>已认证</b>，
 *       字段组成由业务决定，框架<b>不做校验</b>（游客可以只带 {@code attributes}）。</li>
 *   <li>返回的 {@link SecurityUser} 必须<b>一次性给出最终的角色与权限</b>——框架不会二次加载授权信息。</li>
 *   <li>不要在实现里写 {@code SecurityContextHolder}，也不要自行输出错误响应。</li>
 *   <li>日志中不得输出凭证原文（Token / 请求头内容）。</li>
 * </ol>
 *
 * <p>【多来源】框架只取一个实现。需要「请求头 + JWT」这类组合时，在实现内部分派，
 * 而不是注册多个 Bean。
 *
 * @author JYuan
 */
@FunctionalInterface
public interface SecurityUserResolver {

    /**
     * 从请求中解析当前用户（含角色与权限）。
     *
     * @param request 当前请求，不为 {@code null}
     * @return 解析出的用户；<b>无凭证或凭证不可用时返回 {@code null}</b>
     */
    SecurityUser resolve(HttpServletRequest request);
}
