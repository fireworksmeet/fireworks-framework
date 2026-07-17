package com.yzm.fireworks.msg.core.sender;

import com.yzm.fireworks.msg.core.message.EmailMessage;
import com.yzm.fireworks.msg.core.message.MessageResult;
import com.yzm.fireworks.msg.core.ratelimit.RateLimiter;
import com.yzm.fireworks.msg.exception.MessagePushException;
import com.yzm.fireworks.msg.properties.MessageProperties;
import com.yzm.fireworks.msg.utils.TemplateUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 邮件推送服务
 *
 * @author JYuan
 */
@Slf4j
public class EmailSender extends AbstractRateLimitSender<EmailMessage> implements MessageSender<EmailMessage> {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String defaultFrom;

    public EmailSender(JavaMailSender mailSender,
                       TemplateEngine templateEngine,
                       MessageProperties properties,
                       RateLimiter rateLimiter) {
        super(properties, rateLimiter);
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    protected MessageResult doSend(EmailMessage message) {
        sendEmail(message);
        log.info("Email sent successfully: messageId={}", message.getMessageId());
        return MessageResult.success(message.getMessageId());
    }

    @Override
    protected String[] getRecipients(EmailMessage message) {
        return message.getEmailTo();
    }

    @Override
    protected MessageProperties.RateLimitConfig getRateLimitConfig() {
        return properties.getEmail().getRateLimit();
    }

    @Override
    protected String getChannelName() {
        return "Email";
    }

    /**
     * 实际发送邮件
     */
    private void sendEmail(EmailMessage message) {
        String[] recipients = message.getEmailTo();
        if (ObjectUtils.isEmpty(recipients)) {
            throw new MessagePushException("Email recipients are required");
        }

        if (!StringUtils.hasText(defaultFrom)) {
            throw new MessagePushException("Email sender is required");
        }

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper;
        try {
            helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            // 设置发件人
            String fromName = properties.getEmail().getDefaultFromName();
            if (StringUtils.hasText(fromName)) {
                helper.setFrom(defaultFrom, fromName);
            } else {
                helper.setFrom(defaultFrom);
            }

            // 设置收件人
            helper.setTo(recipients);

            // 设置抄送
            if (!ObjectUtils.isEmpty(message.getEmailCc())) {
                helper.setCc(message.getEmailCc());
            }

            // 设置密送
            if (!ObjectUtils.isEmpty(message.getEmailBcc())) {
                helper.setBcc(message.getEmailBcc());
            }

            // 设置主题
            helper.setSubject(StringUtils.hasText(message.getTitle()) ? message.getTitle() : "通知");

            // 设置内容
            String content = buildEmailContent(message);
            helper.setText(content, message.isHtmlEmail());

            // 添加附件
            if (!ObjectUtils.isEmpty(message.getAttachments())) {
                for (String attachmentPath : message.getAttachments()) {
                    File file = new File(attachmentPath);
                    if (file.exists()) {
                        FileSystemResource fileResource = new FileSystemResource(file);
                        helper.addAttachment(file.getName(), fileResource);
                    }
                }
            }
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MessagePushException(e.getMessage(), e);
        }
        mailSender.send(mimeMessage);
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(EmailMessage message) {
        // 如果指定了模板ID，使用Thymeleaf渲染
        if (StringUtils.hasText(message.getTemplateId())) {
            Context context = new Context(Locale.getDefault());
            if (message.getTemplateParams() != null) {
                message.getTemplateParams().forEach(context::setVariable);
            }
            context.setVariable("title", message.getTitle());
            context.setVariable("content", message.getContent());

            /*
             *  模板id对应的是 classpath 下 Thymeleaf 模板文件的路径和名称（不带 .html 后缀）。
             *  如 templates/email/welcome.html 对应的模板id就是 email/welcome
             */
            return templateEngine.process(message.getTemplateId(), context);
        }

        // 否则使用简单模板替换
        if (!ObjectUtils.isEmpty(message.getTemplateParams())) {
            return TemplateUtil.renderTemplate(message.getContent(), message.getTemplateParams());
        }

        return message.getContent();
    }
}
