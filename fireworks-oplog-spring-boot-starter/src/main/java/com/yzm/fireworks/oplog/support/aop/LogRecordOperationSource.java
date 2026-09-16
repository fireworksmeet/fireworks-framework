package com.yzm.fireworks.oplog.support.aop;

import com.yzm.fireworks.common.aop.AbstractAnnotationMetadataSource;
import com.yzm.fireworks.oplog.annotation.LogRecord;
import com.yzm.fireworks.oplog.annotation.LogRecords;
import com.yzm.fireworks.oplog.beans.LogRecordOps;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 操作日志注解元数据源
 * <p>
 * 复用 {@link AbstractAnnotationMetadataSource} 的缓存、防穿透与桥接方法解析能力：
 * <ul>
 *     <li><b>缓存</b>：{@code (Method, Class)} 维度缓存解析结果，避免每次业务调用都重新解析注解</li>
 *     <li><b>防穿透</b>：无注解的方法也会缓存空标记，避免 Pointcut 匹配时的重复解析</li>
 *     <li><b>层次查找</b>：实现类方法 → 实现类 → 接口方法 → 接口，覆盖注解标注在接口上的场景</li>
 *     <li><b>桥接方法</b>：由 {@code AopUtils.getMostSpecificMethod} 一步完成泛型桥接解析</li>
 * </ul>
 * 本类只需实现「如何从元素上解析出元数据」。
 *
 * @author mzt.
 */
public class LogRecordOperationSource extends AbstractAnnotationMetadataSource<Collection<LogRecordOps>> {

    /**
     * 解析指定方法上的操作日志注解
     * <p>
     * 需要两个互补的来源才能覆盖全部写法：
     * <ul>
     *     <li>直接按 {@code LogRecord} 查找：仅能命中<b>单个</b> {@code @LogRecord} 的写法</li>
     *     <li>按容器 {@code LogRecords} 查找并展开：命中重复注解写法
     *         （无论书写为多个 {@code @LogRecord} 还是显式 {@code @LogRecords({...})}）</li>
     * </ul>
     * 两者在同一元素上不会同时命中，合并后即为完整结果。
     *
     * @param element 目标方法或类
     * @return 解析结果；<b>无注解时返回 {@code null}</b>，以便基类缓存"空"结论
     */
    @Override
    @Nullable
    protected Collection<LogRecordOps> findAnnotationMetadata(AnnotatedElement element) {
        // 单个注解由前者命中，重复注解由后者展开，两者互补
        List<LogRecord> annotations = new ArrayList<>(
                AnnotatedElementUtils.findAllMergedAnnotations(element, LogRecord.class));
        annotations.addAll(expandContainers(element));
        if (annotations.isEmpty()) {
            return null;
        }
        // 用 LinkedHashSet 而非 HashSet：既按全字段去重（防御层次查找带来的重叠），
        // 又保持注解的声明顺序，使重复注解按书写顺序依次记录。
        Collection<LogRecordOps> result = new LinkedHashSet<>();
        for (LogRecord annotation : annotations) {
            result.add(parseLogRecordAnnotation(element, annotation));
        }
        return result;
    }

    /**
     * 展开 {@code @LogRecords} 容器注解
     * <p>
     * <b>此步骤必需，不可省略</b>：{@code @LogRecord} 标有 {@code @Repeatable}，
     * 重复书写在编译期即被改写为 {@code @LogRecords} 容器，因此
     * {@code findAllMergedAnnotations(element, LogRecord.class)} <b>查不到</b>这些注解
     * （实测返回空集合），必须改按容器类型查找后展开。
     * <p>
     * 仅在元素上只写<b>单个</b> {@code @LogRecord} 时，上一步才能直接查到，
     * 此时本方法返回空列表，两者结果互补、不会重复。
     */
    private List<LogRecord> expandContainers(AnnotatedElement element) {
        List<LogRecord> result = new ArrayList<>();
        Collection<LogRecords> containers =
                AnnotatedElementUtils.findAllMergedAnnotations(element, LogRecords.class);
        for (LogRecords container : containers) {
            Collections.addAll(result, container.value());
        }
        return result;
    }

    /**
     * 将注解转换为元数据对象
     */
    private LogRecordOps parseLogRecordAnnotation(AnnotatedElement ae, LogRecord recordAnnotation) {
        LogRecordOps recordOps = LogRecordOps.builder()
                .successLogTemplate(recordAnnotation.success())
                .failLogTemplate(recordAnnotation.fail())
                .type(recordAnnotation.type())
                .bizNo(recordAnnotation.bizNo())
                .operator(recordAnnotation.operator())
                .subType(recordAnnotation.subType())
                .extra(recordAnnotation.extra())
                .condition(recordAnnotation.condition())
                .isSuccess(recordAnnotation.successCondition())
                .build();
        validateLogRecordOperation(ae, recordOps);
        return recordOps;
    }

    /**
     * 校验注解配置
     * <p>
     * success 与 fail 至少需配置一个，否则该注解无实际意义。
     * 校验失败会直接抛出异常，使问题在应用启动阶段（代理创建时）即暴露。
     */
    private void validateLogRecordOperation(AnnotatedElement ae, LogRecordOps recordOps) {
        if (!StringUtils.hasText(recordOps.getSuccessLogTemplate())
                && !StringUtils.hasText(recordOps.getFailLogTemplate())) {
            throw new IllegalStateException("LogRecord 注解配置有误，'" + ae
                    + "' 的 success 与 fail 至少需要配置一个");
        }
    }
}
