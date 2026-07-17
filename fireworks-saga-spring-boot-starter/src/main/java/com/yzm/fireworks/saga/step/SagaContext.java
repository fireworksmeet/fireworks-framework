package com.yzm.fireworks.saga.step;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author JYuan
 * <p>
 * Saga上下文
 */
public class SagaContext<T> {

    private final Map<Integer, List<SagaStep<T>>> sortSteps;

    private final Map<String, SagaStep<T>> nameSteps;

    private final Executor executor;

    /**
     * Saga唯一标识
     */
    private final String sagaId;

    /**
     * 存放Saga步骤之间的共享数据
     */
    private final T data;

    /**
     * 存放已经执行的步骤名称
     */
    private final List<String> executedSteps;

    public SagaContext(List<SagaStep<T>> steps, String sagaId, T data) {
        this(steps, sagaId, data, null);
    }

    public SagaContext(List<SagaStep<T>> steps, String sagaId, T data, Executor executor) {
        this(toMap(steps), sagaId, data, executor);
    }

    public SagaContext(Map<SagaStep<T>, Integer> steps, String sagaId, T data) {
        this(steps, sagaId, data, null);
    }

    public SagaContext(Map<SagaStep<T>, Integer> steps, String sagaId, T data, Executor executor) {
        this.sortSteps = getSortMap(steps);
        this.nameSteps = getNameMap(steps);
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.sagaId = sagaId;
        this.data = data;
        this.executedSteps = new CopyOnWriteArrayList<>();
    }

    private static <T> Map<SagaStep<T>, Integer> toMap(List<SagaStep<T>> steps) {
        return IntStream.range(0, steps.size())
                .boxed()
                .collect(Collectors.toMap(steps::get, i -> i));
    }

    private static <T> Map<String, SagaStep<T>> getNameMap(Map<SagaStep<T>, Integer> stepMap) {
        return stepMap.keySet().stream().collect(Collectors.toMap(SagaStep::getName, Function.identity()));
    }

    private static <T> Map<Integer, List<SagaStep<T>>> getSortMap(Map<SagaStep<T>, Integer> stepMap) {
        Map<Integer, List<SagaStep<T>>> sortSteps = Maps.newHashMap();
        for (Map.Entry<SagaStep<T>, Integer> entry : stepMap.entrySet()) {
            SagaStep<T> key = entry.getKey();
            Integer value = entry.getValue();
            if (sortSteps.containsKey(value)) {
                sortSteps.get(value).add(key);
            } else {
                sortSteps.put(value, Lists.newArrayList(key));
            }
        }
        return sortSteps;
    }

    Map<Integer, List<SagaStep<T>>> getSortSteps() {
        return sortSteps;
    }

    Map<String, SagaStep<T>> getNameSteps() {
        return nameSteps;
    }

    Executor getExecutor() {
        return executor;
    }

    String getSagaId() {
        return sagaId;
    }

    T getData() {
        return data;
    }

    List<String> getExecutedSteps() {
        return executedSteps;
    }

}
