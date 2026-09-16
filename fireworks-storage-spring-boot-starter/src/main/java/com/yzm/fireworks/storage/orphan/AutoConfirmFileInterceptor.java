package com.yzm.fireworks.storage.orphan;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code @AutoConfirmFile} 拦截器：只关注业务逻辑。
 * <p>
 * 拦截标注了 {@code @AutoConfirmFile} 的方法，方法执行成功后自动解析方法参数中的文件路径并批量调用
 * {@link OrphanFileGuard#confirm} 确认孤儿文件记录。元数据通过 {@link AutoConfirmFileMetadataSource} 获取
 * 并由父类缓存，首次命中后后续 0 反射。
 * <p>
 * <b>事务语义</b>：自动感知事务——方法在事务中时，等事务提交（{@code afterCommit}）后再确认，保证事务回滚时不误确认；
 * 方法不在事务中时，则方法执行成功后立即确认。
 * <p>
 * 解析出的文件记录若无法确定桶名（非 {@code StorageFile} 等自带桶名的对象、且未配置桶名 SpEL），
 * 会回退到配置 {@code fireworks.storage.orphan-cleanup.default-bucket}；仍无法确定则跳过该条并记录告警。
 * <p>
 * <b>可靠性说明</b>：确认失败意味着文件将持续留在待确认集合中，最终被孤儿清理任务删除。
 * 为降低该风险，确认操作在失败时会重试 {@value #CONFIRM_MAX_ATTEMPTS} 次；
 * 若仍失败，则以 error 级别输出日志并携带影响范围，交由人工介入。
 *
 * @see #doConfirm(List)
 */
@Slf4j
public class AutoConfirmFileInterceptor implements MethodInterceptor, BeanFactoryAware {

    /**
     * 确认失败时的最大尝试次数（含首次）
     */
    private static final int CONFIRM_MAX_ATTEMPTS = 3;

    /**
     * 重试前的退避毫秒数
     */
    private static final long CONFIRM_RETRY_INTERVAL_MILLIS = 100L;

    private final AutoConfirmFileMetadataSource metadataSource;
    private final OrphanFileGuard orphanFileGuard;
    private final OrphanCleanupProperties properties;
    private BeanFactory beanFactory;

    public AutoConfirmFileInterceptor(AutoConfirmFileMetadataSource metadataSource, OrphanFileGuard orphanFileGuard,
            OrphanCleanupProperties properties) {
        this.metadataSource = metadataSource;
        this.orphanFileGuard = orphanFileGuard;
        this.properties = properties;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis() != null ? invocation.getThis().getClass() : null;

        AutoConfirmFileAttribute attribute = metadataSource.getMetadata(method, targetClass);
        Object result = invocation.proceed();
        if (attribute == null || !properties.isEnabled()) {
            return result;
        }


        List<OrphanFile> files = resolveWithBucketFallback(
                attribute, method, invocation.getArguments(), result);
        if (files.isEmpty()) {
            return result;
        }

        // 自动感知事务：事务提交后确认（避免事务回滚时误确认），无事务则执行成功后立即确认。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doConfirm(files);
                }
            });
        } else {
            doConfirm(files);
        }
        return result;
    }

    private List<OrphanFile> resolveWithBucketFallback(AutoConfirmFileAttribute attribute,
                                                       Method method, Object[] args, Object result) {
        List<OrphanFile> files = new ArrayList<>();
        for (OrphanFile file : AutoConfirmFileSupport.resolve(attribute, method, args, result, beanFactory)) {
            String bucket = StringUtils.hasText(file.getBucket()) ? file.getBucket() : properties.getDefaultBucket();
            if (!StringUtils.hasText(bucket)) {
                log.warn("@AutoConfirmFile 无法确定桶名, 跳过确认, objectKey={}。请在注解配置 bucket 或设置 "
                        + "fireworks.storage.orphan-cleanup.default-bucket", file.getObjectKey());
                continue;
            }
            if (StringUtils.hasText(file.getObjectKey())) {
                file.setBucket(bucket);
                files.add(file);
            }
        }
        return files;
    }

    /**
     * 批量确认文件，失败时重试，最终失败升级为 error 告警。
     * <p>
     * <b>为何必须重试</b>：确认的本质是从 Redis 待确认集合中<b>移除</b>这些文件。
     * 若移除失败，文件会一直停留在待确认集合中，待 TTL 到期后被 {@link OrphanFileCleaner}
     * 当作孤儿文件<b>真实删除</b>——即业务正常使用的文件丢失，属数据可靠性事故。
     * Redis 连接抖动、主从切换等瞬时故障是主要失败原因，重试可自动消化这类故障。
     * <p>
     * <b>为何最终只告警不抛出</b>：本方法可能运行在 {@code afterCommit} 回调中，
     * 此时事务已提交无法回滚，抛出异常既无意义，又会污染业务方法的返回语义，
     * 还可能中断同一事务中注册的其他 {@code TransactionSynchronization}。
     * 因此最终失败时记录 error 日志并携带影响范围，由人工介入处理（如补发确认、恢复文件）。
     * <p>
     * <b>为何不做本地队列补偿</b>：本地队列需额外的容量控制、后台线程与优雅停机，
     * 且应用重启会丢失队列内容，收益有限；作为替代，此处通过"重试 + 告警"覆盖绝大多数瞬时故障。
     *
     * @param files 待确认的文件列表，可为空
     */
    private void doConfirm(List<OrphanFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (int attempt = 1; attempt <= CONFIRM_MAX_ATTEMPTS; attempt++) {
            try {
                orphanFileGuard.confirm(files);
                log.info("@AutoConfirmFile 批量确认文件完成, 共 {} 条", files.size());
                return;
            } catch (Exception e) {
                if (attempt < CONFIRM_MAX_ATTEMPTS) {
                    log.warn("@AutoConfirmFile 批量确认文件失败, 共 {} 条, 准备第 {} 次重试",
                            files.size(), attempt, e);
                    if (!sleepBeforeRetry()) {
                        // 线程被中断：不再重试，直接按最终失败处理
                        logFinalFailure(files, attempt, e);
                        return;
                    }
                } else {
                    logFinalFailure(files, attempt, e);
                }
            }
        }
    }

    /**
     * 记录最终失败日志并附带影响范围
     * <p>
     * 使用 error 级别：这是需要人工介入的数据风险，而非可忽略的噪音。
     */
    private void logFinalFailure(List<OrphanFile> files, int attempts, Exception cause) {
        log.error("@AutoConfirmFile 批量确认文件最终失败（已尝试 {} 次），共 {} 个文件将保留在待确认集合中，"
                        + "待 TTL（默认 {}）到期后会被孤儿清理任务删除，存在文件被误删风险，请人工介入处理",
                attempts, files.size(), properties.getDefaultTtl(), cause);
    }

    /**
     * 重试前的短暂退避
     * <p>
     * 退避时间刻意选得很短：本方法可能阻塞在业务线程（无事务时）或事务提交线程上，
     * 退避过长会拖慢业务响应。重试的目的仅是穿过 Redis 瞬时抖动，无需长退避。
     *
     * @return true 表示可继续重试；false 表示线程被中断，应放弃重试
     */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(CONFIRM_RETRY_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

