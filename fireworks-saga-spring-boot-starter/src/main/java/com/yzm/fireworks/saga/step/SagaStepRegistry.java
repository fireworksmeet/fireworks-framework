package com.yzm.fireworks.saga.step;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author JYuan
 */
public class SagaStepRegistry implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;
    private final Map<String, SagaStep<?>> registry = new ConcurrentHashMap<>();

    public SagaStepRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 注册Saga步骤
     */
    void register(SagaStep<?> step) {
        Assert.isTrue(!registry.containsKey(step.getName()), "SagaStep name duplicate: " + step.getName());
        registry.put(step.getName(), step);
    }

    /**
     * 获取Saga步骤
     */
    public SagaStep<?> getStep(String stepName) {
        SagaStep<?> sagaStep = registry.get(stepName);
        Assert.notNull(sagaStep, "No SagaStep registered for: " + stepName);
        return sagaStep;
    }

    /**
     * 检查步骤是否存在
     */
    public boolean containsStep(String stepName) {
        return registry.containsKey(stepName);
    }

    /**
     * 获取所有注册的步骤名称
     */
    public Set<String> getAllStepNames() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    @Override
    public void afterSingletonsInstantiated() {
        applicationContext.getBeansOfType(SagaStep.class, false, false).values().forEach(this::register);
    }
}