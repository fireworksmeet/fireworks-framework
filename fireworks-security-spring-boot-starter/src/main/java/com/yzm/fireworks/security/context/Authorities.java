package com.yzm.fireworks.security.context;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.util.StringUtils;

import java.util.*;

import static com.yzm.fireworks.common.constants.StringPool.EMPTY;

/**
 * 授权信息快照：角色与权限。
 * <p>
 * 由 {@link SecurityUser} 的角色与权限构造，{@link #toGrantedAuthorities(String)} 转成 Spring Security
 * 的 authority 列表：角色加前缀、权限原样。
 * <p>
 * 集合在构造时复制并置为不可变：认证上下文属于身份事实，装配完成后不应被任何调用方改写。
 *
 * @param roles       角色码集合，为 {@code null} 时归一为空集合
 * @param permissions 权限码集合，为 {@code null} 时归一为空集合
 * @author JYuan
 */
public record Authorities(Set<String> roles, Set<String> permissions) {

    public Authorities {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
        permissions = permissions == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
    }

    /**
     * 转换为 Spring Security 的 authorities：角色加前缀（默认 {@code ROLE_}），权限码原样。
     *
     * <p>转换规则：
     * <ul>
     *   <li>前缀与角色码 / 权限码都<b>去除首尾空白</b>（{@code " ADMIN "} → {@code ROLE_ADMIN}）；</li>
     *   <li>{@code null}、空串、纯空白项<b>直接跳过</b>，不产生 {@code ROLE_} 这类无效 authority；</li>
     *   <li>去除首尾空白后去重（{@code "ADMIN"} 与 {@code " ADMIN "} 视为同一条）；</li>
     *   <li>不改变大小写——角色码的大小写由业务约定（Spring 的 {@code hasRole('ADMIN')} 要求大写）。</li>
     * </ul>
     *
     * @param rolePrefix 角色前缀；{@code null}、空串或纯空白表示不加前缀
     */
    public List<GrantedAuthority> toGrantedAuthorities(String rolePrefix) {
        String prefix = rolePrefix == null ? EMPTY : rolePrefix.strip();

        Set<String> merged = new LinkedHashSet<>();
        addCodes(merged, roles, prefix);
        addCodes(merged, permissions, EMPTY);

        return AuthorityUtils.createAuthorityList(merged);
    }

    /**
     * 归一化并拼接前缀。
     *
     * <p><b>与 {@link SecurityUser} 的构造归一化重复是有意的</b>：{@code SecurityUser} 保证从它构造时
     * 值已干净，但 {@code Authorities} 可以被独立构造（测试、其它调用方），这里再守一道。
     * 两次 {@code strip} 是幂等的，没有副作用。
     */
    private static void addCodes(Set<String> target, Set<String> codes, String prefix) {
        for (String raw : codes) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            // strip 与 hasText 使用同一套空白定义（Character.isWhitespace），避免 trim 留下全角空格
            target.add(prefix + raw.strip());
        }
    }
}
