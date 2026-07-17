package com.yzm.fireworks.saga.step;

import java.util.List;

/**
 * @author JYuan
 */
public interface SagaStep<T> {

    /**
     * 获取步骤名称
     *
     * @return 步骤名称
     */
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 一阶段正向服务
     *
     * @param param 参数
     */
    void execute(T param);

    /**
     * 二阶段补偿服务
     *
     * @param param 参数
     */
    void compensate(T param);


    /**
     * 用于二阶段失败后的定时补偿服务
     *
     * @param params 参数集合
     */
    void compensate(List<T> params);
}