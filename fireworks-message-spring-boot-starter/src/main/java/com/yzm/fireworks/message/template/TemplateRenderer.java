package com.yzm.fireworks.message.template;

import java.util.Map;

/**
 * 模板渲染。
 *
 * @author JYuan
 */
public interface TemplateRenderer {

    /**
     * 渲染模板。
     *
     * @param templatePath Thymeleaf 模板路径（不带 {@code .html}）
     * @param params       模板参数
     * @return 渲染结果；路径为空时返回 {@code null}
     */
    String render(String templatePath, Map<String, Object> params);
}
