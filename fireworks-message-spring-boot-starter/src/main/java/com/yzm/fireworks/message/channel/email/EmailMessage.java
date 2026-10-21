package com.yzm.fireworks.message.channel.email;

import com.yzm.fireworks.message.domain.ChannelMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 邮件渠道入参。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class EmailMessage implements ChannelMessage {

    /**
     * 消息标识；由门面从 {@code MessageEnvelope} 复制，用于构造结果。
     */
    private String messageId;

    /**
     * 收件人地址列表；必填。
     */
    private List<String> to;

    /**
     * 邮件主题；必填，对应 {@code MessageEnvelope.title}。
     */
    private String subject;

    /**
     * 正文；存在模板路径时以渲染结果为准。
     */
    private String content;

    /**
     * Thymeleaf 模板路径（不带 {@code .html}），可空；为空时直接使用 {@code content}。
     */
    private String templatePath;

    /**
     * 模板参数。
     */
    private Map<String, Object> params;

    /**
     * content 是否为 HTML；默认 false。
     */
    private boolean html;
}
