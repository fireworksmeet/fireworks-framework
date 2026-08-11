package com.yzm.fireworks.storage.core.orphan;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.storage.api.StorageFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.yzm.fireworks.common.constants.StringPool.COLON;

/**
 * {@code @AutoConfirmFile} 拦截器使用的文件提取辅助类：负责把方法参数解析为待确认的
 * {@link PendingFile} 集合（复用已有的待确认文件实体，仅填充 bucket / objectKey）。
 * <p>
 * 规约覆盖三种主流形态：前端直传后传给后端保存的 {@code String} 或 {@code String} 集合/数组，
 * 以及后端上传返回的 {@link StorageFile}（携带 bucket 与 objectKey）。不做反射猜测，行为完全可预期。
 */
@Slf4j
public class AutoConfirmFileSupport {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private AutoConfirmFileSupport() {
    }

    /**
     * 从方法参数或返回值中解析出待确认文件集合。
     * <p>
     * 仅通过 {@link AutoConfirmFileAttribute#getObjectKey()} 指定的 SpEL 精确提取，不做盲目扫描：
     * SpEL 为空时返回空列表；SpEL 解析结果由 {@link #toPendingFiles} 按类型规约为待确认文件。
     *
     * @param attribute   元数据（含 bucket / objectKey SpEL 表达式）
     * @param method      被代理方法
     * @param args        方法参数
     * @param result      方法返回值（可通过 {@code #result} 访问，如后端上传返回的 {@code StorageFile}）
     * @param beanFactory 可选，用于 SpEL 中引用 Bean
     * @return 去重后的待确认文件（bucket 可能为空，表示无法确定桶名，调用方需处理）
     */
    public static List<PendingFile> resolve(AutoConfirmFileAttribute attribute, Method method, Object[] args,
            Object result, BeanFactory beanFactory) {
        if (attribute == null || attribute.getObjectKeyExpression() == null) {
            return Collections.emptyList();
        }
        EvaluationContext context = buildContext(method, args, result, beanFactory);
        Object value = attribute.getObjectKeyExpression().getValue(context);
        String defaultBucket = attribute.getBucketExpression() != null
                ? evalToString(attribute.getBucketExpression().getValue(context))
                : null;
        return dedupe(toPendingFiles(value, defaultBucket));
    }


    private static EvaluationContext buildContext(Method method, Object[] args, Object result, BeanFactory beanFactory) {
        // rootObject 传 null：业务通过 #args / #result / 参数名 访问，不使用 #root 根对象。
        // rootObject 参数在 Spring 中标注 @Nullable（官方 javadoc 确认），传 null 是合法且期望的。
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(null, method, args,
                PARAMETER_NAME_DISCOVERER);
        context.setVariable("args", args);
        context.setVariable("result", result);
        if (beanFactory != null) {
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
        return context;
    }

    private static Object eval(String expression, EvaluationContext context) {
        Expression exp = PARSER.parseExpression(expression);
        return exp.getValue(context);
    }

    private static String evalToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 把 SpEL 解析结果规约为待确认文件集合，覆盖三种主流形态：
     * <ul>
     *     <li>{@link StorageFile}：后端上传的返回对象，拆出 bucket 与 objectKey；</li>
     *     <li>{@link String}：前端直传后传给后端保存的对象名；</li>
     *     <li>{@code Collection} / {@code Iterable} / 数组：递归展开上述类型。</li>
     * </ul>
     */
    private static List<PendingFile> toPendingFiles(Object value, String defaultBucket) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<PendingFile> files = new ArrayList<>();
        if (value instanceof StorageFile sf) {
            files.add(PendingFile.builder()
                    .bucket(StringUtils.hasText(sf.getBucketName()) ? sf.getBucketName() : defaultBucket)
                    .objectKey(sf.getObjectKey())
                    .build());
        } else if (value instanceof String s) {
            if (StringUtils.hasText(s)) {
                files.add(PendingFile.builder().bucket(defaultBucket).objectKey(s).build());
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                files.addAll(toPendingFiles(item, defaultBucket));
            }
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                files.addAll(toPendingFiles(Array.get(value, i), defaultBucket));
            }
        } else {
            log.warn("@AutoConfirmFile 解析结果不是 StorageFile / String 或其集合/数组，已忽略。"
                    + "objectKey 表达式应指向 StorageFile、String 或 List<String>，当前类型: {}",
                    value.getClass().getName());
        }
        return files;
    }

    private static List<PendingFile> dedupe(List<PendingFile> files) {
        // PendingFile 是 @Data，equals/hashCode 由所有字段决定，此处按 bucket+objectKey 去重。
        Set<String> seen = new LinkedHashSet<>();
        List<PendingFile> result = new ArrayList<>();
        for (PendingFile file : files) {
            String key = file.getBucket() + COLON + file.getObjectKey();
            if (seen.add(key)) {
                result.add(file);
            }
        }
        return result;
    }
}
