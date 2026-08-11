package com.yzm.fireworks.storage.core.orphan;

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
 */
@Slf4j
public class AutoConfirmFileInterceptor implements MethodInterceptor, BeanFactoryAware {

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


        List<PendingFile> files = resolveWithBucketFallback(
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

    private List<PendingFile> resolveWithBucketFallback(AutoConfirmFileAttribute attribute,
            Method method, Object[] args, Object result) {
        List<PendingFile> files = new ArrayList<>();
        for (PendingFile file : AutoConfirmFileSupport.resolve(attribute, method, args, result, beanFactory)) {
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

    private void doConfirm(List<PendingFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            orphanFileGuard.confirm(files);
            if (log.isInfoEnabled()) {
                log.info("@AutoConfirmFile 批量确认文件完成, 共 {} 条", files.size());
            }
        } catch (Exception e) {
            log.warn("@AutoConfirmFile 批量确认文件异常, reason={}", e.getMessage());
        }
    }
}

