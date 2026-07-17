package com.yzm.fireworks.common.util;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.util.Assert;

/**
 * 密码工具类
 *
 * <p>职责：用户密码的加密与校验，仅此一项。
 *
 * <p>为什么用 BCrypt：
 * <ul>
 *   <li>内置随机盐，相同密码每次加密结果不同，天然防彩虹表。</li>
 *   <li>cost 参数控制计算成本，硬件升级后可调高，已加密的密码无需重加密。</li>
 *   <li>依赖 org.mindrot:jbcrypt，fireworks-common 已引入，无需额外依赖。</li>
 * </ul>
 *
 * <p>不要用此类做非密码场景的哈希，手机号/邮箱等查询型哈希请用 {@link HashUtil#hashWithSalt}。
 *
 * @author JYuan
 */
public final class PasswordUtil {

    /**
     * cost=12：加密一次约 300ms，兼顾安全性与性能。
     * cost=10 是 jbcrypt 常用默认值，约 100ms，可根据服务器性能调整，建议不低于 10。
     */
    private static final int COST = 12;

    private PasswordUtil() {
        throw new AssertionError("Utility class");
    }

    /**
     * 加密密码
     *
     * @param rawPassword 明文密码
     * @return BCrypt 哈希值（含盐，可直接存库）
     */
    public static String encode(String rawPassword) {
        Assert.hasText(rawPassword, "rawPassword must not be blank");
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    /**
     * 校验密码
     *
     * @param rawPassword     明文密码
     * @param encodedPassword 库中存储的哈希值
     * @return true=匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        Assert.hasText(rawPassword, "rawPassword must not be blank");
        Assert.hasText(encodedPassword, "encodedPassword must not be blank");
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}