package com.yzm.fireworks.id;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分布式 ID 工具类（基于 CosId，支持号段与雪花两种模式）。
 * <p>
 * <b>模式</b>：由 CosId 自身的开关选择——{@code cosid.segment.enabled}（号段模式）或
 * {@code cosid.snowflake.enabled}（雪花模式），两者互斥（同时开启会启动失败，模板见 README）。
 * 两种模式共用本工具类：{@link #getId(String)} 对两者行为一致，<b>切换模式业务代码零改动</b>。
 * <p>
 * <b>ID 特性随模式不同，务必区分</b>：
 * <ul>
 *   <li><b>号段模式</b>：单实例内单调递增；全局仅“趋势递增”（多实例各持号段，同一时刻会乱序，
 *       乱序程度由 {@code step} 与集群规模决定）；不保证连续。ID 短，起点由 {@code offset} 精确控制。</li>
 *   <li><b>雪花模式</b>：<b>时间有序</b>（同一实例严格递增，跨实例按时间戳近似有序）；无数据库往返；
 *       ID 形态由位分配决定（默认约 19 位十进制）。</li>
 * </ul>
 * 两种模式生成的是<b>不同的号源</b>，同一业务编号不要中途切换（会更号、并破坏位数契约）。
 * <p>
 * <b>业务标识由使用方自行定义</b>：本模块不提供任何业务枚举（那是业务侧的事），标识就是一个字符串常量——
 * 约定放在使用方自己的枚举 / 常量类里，值需与 CosId 的 provider 配置名一致。
 * <p>
 * <b>使用前提</b>：标识必须先在对应模式下声明，CosId 才会注册生成器——号段模式为
 * {@code cosid.segment.provider}，雪花模式为 {@code cosid.snowflake.provider}；
 * 未声明的标识在调用时直接抛 {@link IllegalArgumentException}。
 * <p>
 * <b>不需要标识的场景</b>：技术性标识（traceId、消息 ID、幂等键、临时 token）不关心位数与业务归属，
 * 用 {@link #getShareId()} 直接读 CosId 的共享生成器即可，无需声明业务标识。
 * 反之——<b>业务编号一律用 {@link #getId(String)}</b>，不要图省事走 keyless 方法，它的形态随模式变化。
 * <p>
 * <b>失败语义</b>：一律 fail-fast 抛异常（应用尚未启动完成 / 标识未声明），
 * <b>不返回 {@code -1} 之类的哨兵值</b>——哨兵值会被静默写进订单号、会员号等业务数据，属于不可挽回的事故。
 * <p>
 * <b>为什么本类会被注册成一个 Bean</b>：静态 API 拿不到依赖注入，必须有个 Bean 在启动时把容器里的
 * {@link IdGeneratorProvider} 交过来——由 {@link IdAutoConfiguration} 创建本类实例完成，赋值就发生在这个
 * <b>构造器</b>里（不需要任何生命周期钩子）。此后每次取号只是一次引用读取 + 一次映射查找，不必反复查 Bean；
 * 容器重建（集成测试多上下文、DevTools 重启）时新容器会新建实例、重新绑定，不会残留旧上下文的对象。
 * <p>
 * 因此本类的实例没有任何用途，<b>不要注入使用</b>——构造器包内可见，也不对外暴露。
 */
public final class IdUtil {

    /** 由 {@link IdAutoConfiguration} 在创建实例时绑定，此后只读 */
    private static volatile IdGeneratorProvider provider;

    /**
     * 绑定 provider（包内可见，只由 {@link IdAutoConfiguration} 调用）。
     * <p>
     * 绑的是<b>引用</b>而不是快照：CosId 的生成器注册器（{@code XxxBeanRegistrar}）晚于本构造器执行也没关系——
     * 它们往 provider 里注册生成器之后，这里读到的就是同一份内容，所以无需等到「所有单例就绪」。
     */
    IdUtil(IdGeneratorProvider idGeneratorProvider) {
        provider = idGeneratorProvider;
    }

    /**
     * 获取 <b>32 位紧凑格式</b>的 UUID（版本 4，<b>无连字符</b>），如
     * {@code 1450421b6a4645cb8a203e498c6d6502}。
     * <p>
     * <b>为什么去连字符</b>：框架内 storage 的 {@code ObjectKeyUtil.uuid()}、export 的
     * {@code ExcelWebUtil.uuid()} 也生成随机标识，此前只有本方法带连字符——统一为紧凑格式，
     * 避免同一框架出现两种 UUID 形态。值与标准形式完全一致，只是省略了分隔符。
     * <p>
     * <b>解析注意</b>：{@link UUID#fromString(String)} <b>不接受</b>这种无连字符写法
     * （实测 JDK 21 抛 {@code IllegalArgumentException: Invalid UUID string}）；
     * 需要反解时先按 {@code 8-4-4-4-12} 插入连字符，解析结果与紧凑形式的位完全一致。
     * <p>
     * 注意：UUID 不适用于订单号 / 会员号这类需要“短且趋势递增”的业务编号，
     * 它只适合对索引局部性无要求的场景。若需要“唯一 + 有序”的标识（如链路追踪 ID），
     * 优先用 {@link #getShareId()}——号段模式单实例自增、雪花模式时间有序，且无需声明业务标识。
     */
    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 按业务标识获取分布式 ID。
     *
     * @param key 业务标识：号段模式在 {@code cosid.segment.provider} 下声明，
     *            雪花模式在 {@code cosid.snowflake.provider} 下声明
     * @return 全局唯一的 64 位 ID
     * @throws IllegalArgumentException key 为空、或该标识未声明
     * @throws IllegalStateException    应用尚未启动完成（provider 未绑定）
     */
    public static long getId(String key) {
        return generator(key).generate();
    }

    /**
     * 按业务标识获取<b>字符串形态</b>的分布式 ID（{@link IdGenerator#generateAsString()}）。
     * <p>
     * 与 {@link #getId(String)} 是<b>同一个号源</b>，只是输出形态不同——它经过 CosId 的 {@code IdConverter}：
     * <ul>
     *   <li><b>默认是 radix62</b>（0-9A-Za-z）且<b>定长 11 位左补零</b>（62<sup>11</sup> &gt; {@code Long.MAX_VALUE}，
     *       11 位足够表示任意非负 long）：十进制 {@code 102} → {@code "0000000001e"}</li>
     *   <li>在该标识下配置 {@code converter}（{@code radix} / {@code radix36} / {@code to-string} /
     *       {@code date-prefix} / {@code friendly} / {@code group-prefix} / {@code custom}）可改形态，
     *       其中 {@code radix.char-size} / {@code radix.pad-start} 可调长度与补零</li>
     * </ul>
     * 注意两者<b>不是同一份文本</b>：{@code getIdAsString(key)} 不等于 {@code String.valueOf(getId(key))}；
     * 需要十进制字符串时直接用 {@link #getId(String)}。
     *
     * @param key 业务标识，同 {@link #getId(String)}
     * @return 转换后的字符串 ID
     * @throws IllegalArgumentException key 为空、或该标识未声明
     * @throws IllegalStateException    应用尚未启动完成（provider 未绑定）
     */
    public static String getIdAsString(String key) {
        return generator(key).generateAsString();
    }

    /**
     * 获取 CosId <b>共享生成器</b>（{@code __share__}）的 ID——<b>不需要业务标识</b>。
     * <p>
     * <b>适用</b>：技术性标识，不关心位数与业务归属——traceId、消息 ID、幂等键、临时 token 等。
     * <p>
     * <b>不要用于业务编号</b>（订单号 / 会员号 / 流水号），两个原因：
     * <ul>
     *   <li>它的形态<b>随所选模式变化</b>——号段模式是短号、雪花模式约 19 位；业务编号的位数是有契约的，
     *       一旦换模式即被破坏（同 {@link #getId(String)} 的警告）</li>
     *   <li>它没有业务域名，读代码的人无从判断这个号属于哪个业务</li>
     * </ul>
     * 业务编号请用 {@link #getId(String)} 显式声明标识——多写一行配置，换回可控的号源与明确的归属。
     * <p>
     * 共享生成器由 CosId 自行按 {@code cosid.segment.share.enabled} / {@code cosid.snowflake.share.enabled}
     * 注册（<b>默认均为 {@code true}</b>；号段模式下首次取号才建行，不调用则零成本）。本框架为两种模式加了互斥，
     * 因此同一应用里共享生成器最多只有一个——这正是它能当“全局号源”用的前提。
     *
     * @return 全局唯一的 64 位 ID
     * @throws IllegalArgumentException 共享生成器未注册（被显式关闭）
     * @throws IllegalStateException    应用尚未启动完成（provider 未绑定）
     */
    public static long getShareId() {
        return shareGenerator().generate();
    }

    /**
     * 获取共享生成器 ID 的<b>字符串形态</b>（{@link IdGenerator#generateAsString()}）。
     * <p>
     * 与 {@link #getShareId()} 是<b>同一个号源</b>，只是经过 CosId 的 {@code IdConverter}：
     * 默认 radix62、定长 11 位左补零，可用 {@code cosid.segment.share.converter.*} /
     * {@code cosid.snowflake.share.converter.*} 改形态（如加日期前缀、改长度）。
     * <p>
     * 技术 ID 常要写进日志、HTTP 头、MQ key，定长且无符号的字符串比十进制更好对齐与排序，
     * 因此这不是 {@code Long.toString(getShareId())} 的替代品——它<b>不等价</b>（同 {@link #getIdAsString(String)}）。
     *
     * @return 转换后的字符串 ID
     * @throws IllegalArgumentException 共享生成器未注册（被显式关闭）
     * @throws IllegalStateException    应用尚未启动完成（provider 未绑定）
     */
    public static String getShareIdAsString() {
        return shareGenerator().generateAsString();
    }

    /**
     * 解析共享生成器；未注册（被显式关闭）时 fail-fast。
     */
    private static IdGenerator shareGenerator() {
        IdGenerator share = requireProvider().getShare();
        Assert.notNull(share,
                "共享生成器未注册：请确认当前模式的 share 开关为 true（cosid.segment.share.enabled / "
                        + "cosid.snowflake.share.enabled，默认即 true），或改用带业务标识的 getId(String)");
        return share;
    }

    /**
     * 解析业务标识对应的生成器。
     */
    private static IdGenerator generator(String key) {
        Assert.hasText(key, "业务标识不能为空");

        IdGeneratorProvider current = requireProvider();
        return current.get(key).orElseThrow(() -> new IllegalArgumentException(
                "未声明业务标识 [%s]：号段模式请在 cosid.segment.provider 下声明，雪花模式请在 cosid.snowflake.provider 下声明（当前已注册：%s）"
                        .formatted(key, configuredKeys(current))));
    }

    /**
     * 取已绑定的 provider；未启动完成时 fail-fast（不返回 {@code null} 让调用方各自判断）。
     */
    private static IdGeneratorProvider requireProvider() {
        IdGeneratorProvider current = provider;
        Assert.state(current != null,
                "IdUtil 尚未完成初始化：请在 Spring 应用启动完成之后调用（provider 由 IdAutoConfiguration 绑定）");
        return current;
    }

    /**
     * 汇总已注册的业务标识，用于“标识未配置”时的排查提示。
     */
    private static String configuredKeys(IdGeneratorProvider provider) {
        return provider.entries().stream()
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
