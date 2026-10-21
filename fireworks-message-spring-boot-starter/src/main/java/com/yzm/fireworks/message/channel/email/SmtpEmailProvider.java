package com.yzm.fireworks.message.channel.email;

import com.yzm.fireworks.message.domain.ChannelResult;
import com.yzm.fireworks.message.domain.MessageEnvelope;
import com.yzm.fireworks.message.domain.ProviderType;
import com.yzm.fireworks.message.domain.Recipient;
import com.yzm.fireworks.message.exception.MessageValidationException;
import com.yzm.fireworks.message.properties.MessageProperties;
import com.yzm.fireworks.message.template.TemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * SMTP 邮件实现。
 *
 * @author JYuan
 */
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;
    private final TemplateRenderer templateRenderer;
    private final MessageProperties.Email config;
    /**
     * 最终发件地址：优先取 {@code fireworks.message.channels.email.from}，否则取 {@code spring.mail.username}。
     */
    private final String fromAddress;

    /**
     * @param mailSender       JavaMailSender
     * @param templateRenderer 模板渲染器，可为 null（不使用模板时）
     * @param properties       消息配置
     * @param mailUsername     {@code spring.mail.username}，仅作为 {@code from} 的兜底值
     */
    public SmtpEmailProvider(JavaMailSender mailSender,
                             TemplateRenderer templateRenderer,
                             MessageProperties properties,
                             String mailUsername) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.config = properties.getChannels().getEmail();
        this.fromAddress = StringUtils.hasText(config.getFrom()) ? config.getFrom() : mailUsername;
    }

    @Override
    public ProviderType providerType() {
        return ProviderType.SMTP;
    }

    @Override
    public EmailMessage convert(MessageEnvelope envelope) {
        Recipient recipient = envelope.getRecipient();
        if (recipient == null || CollectionUtils.isEmpty(recipient.getEmailAddresses())) {
            throw new MessageValidationException("emailAddresses is required for EMAIL");
        }
        if (!StringUtils.hasText(fromAddress)) {
            throw new MessageValidationException("mail sender is required, set fireworks.message.channels.email.from or spring.mail.username");
        }
        if (!StringUtils.hasText(envelope.getTitle())) {
            throw new MessageValidationException("subject is required for EMAIL");
        }
        if (StringUtils.hasText(envelope.getTemplate()) && templateRenderer == null) {
            throw new MessageValidationException("template is set but no TemplateRenderer is available");
        }

        return EmailMessage.builder()
                .messageId(envelope.getMessageId())
                .to(recipient.getEmailAddresses())
                .subject(envelope.getTitle())
                .content(envelope.getContent())
                .templatePath(envelope.getTemplate())
                .params(envelope.getParams())
                .html(envelope.isEmailHtml())
                .build();
    }

    @Override
    public ChannelResult send(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            // 设置显式名称，不是设置多个发件人，而是 一个地址 + 一个显式名
            // 忽略 null 的隐式行为，调用处看不出「没有显示名」的意图。保留两个分支，各自表达一种情况。
            if (StringUtils.hasText(config.getFromName())) {
                helper.setFrom(fromAddress, config.getFromName());
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(message.getTo().toArray(new String[0]));
            helper.setSubject(message.getSubject());

            String content = buildContent(message);
            helper.setText(content == null ? "" : content, message.isHtml());

            mailSender.send(mimeMessage);
            return new ChannelResult.Success(message.getMessageId(), null);
        } catch (MailAuthenticationException e) {
            return new ChannelResult.PermanentFailure(message.getMessageId(), "AUTH_FAILED", e.getMessage());
        } catch (MailParseException | MessagingException | UnsupportedEncodingException e) {
            return new ChannelResult.PermanentFailure(message.getMessageId(), "INVALID_MESSAGE", e.getMessage());
        } catch (MailException e) {
            return new ChannelResult.RetryableFailure(message.getMessageId(), "MAIL_SEND_FAILED", e.getMessage(), e);
        }
    }

    private String buildContent(EmailMessage message) {
        if (StringUtils.hasText(message.getTemplatePath())) {
            return templateRenderer.render(message.getTemplatePath(), message.getParams());
        }
        return message.getContent();
    }
}
