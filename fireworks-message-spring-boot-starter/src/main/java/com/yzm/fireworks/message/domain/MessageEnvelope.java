package com.yzm.fireworks.message.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一消息模型。
 * <p>
 * 组合式而非继承：新增渠道不改模型，消息可整体序列化用于 MQ / outbox。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class MessageEnvelope {

    /**
     * 唯一消息标识，必填。
     * <p>
     * 由业务提供：写 outbox 时就应确定，重放与重试时复用。框架只校验非空，不生成、不改写。
     */
    private String messageId;

    /**
     * 投递渠道，必填。
     * <p>
     * 决定使用哪个 Provider、读取哪一段渠道配置、适用哪一条限流规则。
     */
    private ChannelType channel;

    /**
     * 接收者，必填。
     * <p>
     * 按渠道读取对应字段：短信读 phoneNumbers，邮件读 emailAddresses，实时读 channelIds；
     * 其余字段不参与本次发送。
     */
    private Recipient recipient;

    /**
     * 标题。
     * <p>
     * 邮件作为主题，必填；实时推送作为消息标题，可空；短信忽略。
     */
    private String title;

    /**
     * 正文。
     * <p>
     * 邮件可直接作为正文（是否 HTML 见 {@link #emailHtml}），实时推送作为消息正文；
     * 短信忽略该字段——短信必须使用厂商模板，参数由业务通过 params 提供，框架不做任何注入。
     */
    private String content;

    /**
     * 模板标识，含义由渠道决定。
     * <p>
     * 短信填厂商模板 ID（对应配置 {@code channels.sms.template-id}，缺失时回退到该配置）；
     * 邮件填 Thymeleaf 模板路径（不带 {@code .html}）；实时通道忽略。
     */
    private String template;

    /**
     * 模板参数，键名与模板占位符一致；仅短信与邮件使用。
     * <p>
     * 短信透传给厂商（腾讯云按值顺序填充，注意 Map 的有序性）；
     * 邮件用于 Thymeleaf 变量或无模板时的 ${key} 替换。
     */
    private Map<String, Object> params;

    /**
     * 实时通道：业务载荷，其余渠道忽略。
     * <p>
     * 任意可 JSON 序列化的对象，直接放入 DTO 即可，不需要先转成 Map；框架原样写入消息体 data。
     */
    private Object realtimeData;

    /**
     * 实时通道：publish 路径，REALTIME 必填，其余渠道忽略。
     * <p>
     * 绝对 URL（http / https）直接使用，相对路径拼在
     * {@code fireworks.message.channels.realtime.nchan.url} 之后。
     */
    private String realtimePath;

    /**
     * 邮件通道：正文是否按 HTML 发送，默认 false，其余渠道忽略。
     */
    private boolean emailHtml;

    /**
     * 限流维度后缀，默认省略。
     * <p>
     * 拼在限流键 {@code channel:providerType} 之后，用于把同一渠道按业务维度拆成多个限流桶。
     */
    private String rateLimitScope;

    /**
     * 业务自定义元数据，可为空；框架不读取其中的键。
     * <p>
     * 供业务在 {@code MessageListener}、outbox 记录里携带自己的标记，不参与渠道发送。
     */
    private Map<String, Object> metadata;
}
