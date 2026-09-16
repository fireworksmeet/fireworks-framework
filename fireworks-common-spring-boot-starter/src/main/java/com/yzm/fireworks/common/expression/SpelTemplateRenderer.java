package com.yzm.fireworks.common.expression;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL 模板渲染器
 * <p>
 * 负责把「静态文案 + 动态占位符」混排的模板渲染为最终字符串。
 * 占位符语法为 <b>{@code #{表达式}}</b>，与 Spring 的
 * {@code @Value}、{@code @ConditionalOnExpression} 保持一致：
 *
 * <pre>{@code
 * "用户 #{#user.name} 修改了订单"
 * "#{#orderNo} 的数量为 #{#count}"
 * }</pre>
 *
 * <p>
 * 切分与求值由 Spring 官方的 {@link TemplateParserContext} 完成，本类不自行
 * 解析表达式边界。相比手写正则，官方实现具备两个关键优势：
 * <ul>
 *     <li><b>理解 SpEL 语法</b>：字符串字面量中的 {@code }} 不会被误判为结束标记，
 *         如 {@code #{'a}b'}} 可正确求值为 {@code a}b</li>
 *     <li><b>支持嵌套花括号</b>：SpEL 的 inline map 语法
 *         （{@code #{{'a':1}}}）可正常解析</li>
 * </ul>
 *
 * <p>
 * 表达式求值为 {@code null} 时渲染为空串（由 Spring 内部处理），
 * 不会出现字面量 {@code "null"}。
 * <p>
 * 本类无状态，可安全复用。
 *
 * @author JYuan
 */
public final class SpelTemplateRenderer {

    /**
     * 模板上下文：前缀 {@code "#{"}、后缀 {@code "}"}
     * <p>
     * 使用默认构造，即与 Spring 其他注解完全一致的占位符语法。
     */
    private static final ParserContext TEMPLATE_CONTEXT = new TemplateParserContext();

    /**
     * 表达式解析器，无状态可复用
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 占位符识别正则，仅用于「判断是否含占位符」与「提取表达式文本」，
     * 不参与模板切分（切分由 {@link TemplateParserContext} 负责）
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(.*?)}");

    private SpelTemplateRenderer() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 解析模板为表达式
     * <p>
     * 模板将成为 {@code CompositeStringExpression}：静态片段与动态表达式交替，
     * 求值时自动完成拼接。
     *
     * @param template 模板字符串
     * @return 已解析的表达式
     */
    public static Expression parse(String template) {
        return PARSER.parseExpression(template, TEMPLATE_CONTEXT);
    }

    /**
     * 判断模板中是否包含占位符
     */
    public static boolean containsPlaceholder(@Nullable String template) {
        return template != null && template.contains("#{");
    }

    /**
     * 提取模板中出现的全部表达式
     * <p>
     * 用于「先收集后求值」的场景，例如提前判断模板引用了哪些保留变量。
     *
     * @param templates 模板集合
     * @return 去重后的表达式列表，保持出现顺序
     */
    public static List<String> extractExpressions(Iterable<String> templates) {
        LinkedHashSet<String> expressions = new LinkedHashSet<>();
        for (String template : templates) {
            if (!containsPlaceholder(template)) {
                continue;
            }
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                expressions.add(matcher.group(1).trim());
            }
        }
        return new ArrayList<>(expressions);
    }

    /**
     * 判断模板是否为「单个纯表达式」
     * <p>
     * 形如 {@code "#{#_result != null}"} 的模板整个就是一个表达式，
     * 可直接按布尔或指定类型求值，而无需先渲染为字符串再做字符串比较。
     *
     * @param template 模板字符串
     * @return 整个模板恰好是一个占位符时返回表达式文本，否则返回 {@code null}
     */
    @Nullable
    public static String asSingleExpression(@Nullable String template) {
        if (template == null) {
            return null;
        }
        String trimmed = template.trim();
        if (trimmed.startsWith("#{") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1).trim();
        }
        return null;
    }
}
