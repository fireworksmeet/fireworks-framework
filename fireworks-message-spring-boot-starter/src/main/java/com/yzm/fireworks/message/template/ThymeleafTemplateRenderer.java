package com.yzm.fireworks.message.template;

import com.yzm.fireworks.message.exception.MessageValidationException;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf 模板渲染。
 *
 * @author JYuan
 */
public class ThymeleafTemplateRenderer implements TemplateRenderer {

    private final TemplateEngine templateEngine;

    public ThymeleafTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public String render(String templatePath, Map<String, Object> params) {
        if (!StringUtils.hasText(templatePath)) {
            return null;
        }
        if (templateEngine == null) {
            throw new MessageValidationException("Thymeleaf TemplateEngine is not available");
        }
        Context context = new Context(Locale.getDefault());
        if (params != null) {
            params.forEach(context::setVariable);
        }
        return templateEngine.process(templatePath, context);
    }
}
