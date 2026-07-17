package com.yzm.fireworks.common.util;


import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * @author JYuan
 * spring 应用上下文工具类
 */
@Component
public class ApplicationContextUtil implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    public static ApplicationContext getApplicationContext() {
        Assert.notNull(applicationContext, "Failed to obtain the ApplicationContext");
        return applicationContext;
    }

    /**
     * 根据类型从容器中获取bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 根据名称从容器中获取bean
     */
    public static Object getBean(String name) throws BeansException {
        return getApplicationContext().getBean(name);
    }

    /**
     * 根据名称和类型从容器中获取bean
     */
    public static <T> T getBean(String name, Class<T> clazz) throws BeansException {
        return getApplicationContext().getBean(name, clazz);
    }

    /**
     * 注册Bean到容器中
     */
    public static <T> void registerBean(Class<T> clazz) {
        registerBean(String.class.getSimpleName(), clazz, null);
    }

    /**
     * 注册Bean到容器中
     */
    public static <T> void registerBean(Class<T> clazz, Map<String, Object> propertyValues) {
        registerBean(String.class.getSimpleName(), clazz, propertyValues);
    }

    /**
     * 注册Bean到容器中
     */
    public static <T> void registerBean(String beanName, Class<T> clazz, Map<String, Object> propertyValues, Object... constructorArgs) {
        Assert.hasText(beanName, "beanName can't be null");
        Assert.notNull(clazz, "Class can't be null");
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (!ObjectUtils.isEmpty(constructorArgs)) {
            for (Object constructorArg : constructorArgs) {
                // 会根据添加的顺序，绑定对应的位置
                builder.addConstructorArgValue(constructorArg);
            }
        }
        if (!ObjectUtils.isEmpty(propertyValues)) {
            for (String key : propertyValues.keySet()) {
                if (StringUtils.hasText(key)) {
                    builder.addPropertyValue(key, propertyValues.get(key));
                }
            }
        }
        AbstractBeanDefinition rawBeanDefinition = builder.getRawBeanDefinition();
        AutowireCapableBeanFactory autowireCapableBeanFactory = ApplicationContextUtil.getApplicationContext().getAutowireCapableBeanFactory();
        if (autowireCapableBeanFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) autowireCapableBeanFactory;
            beanFactory.registerBeanDefinition(beanName, rawBeanDefinition);
        }
    }

    /**
     * 发布事件
     */
    public static void publishEvent(ApplicationEvent event) {
        getApplicationContext().publishEvent(event);
    }

    /**
     * 发布事件
     */
    public static <T> void publishEvent(T event) {
        getApplicationContext().publishEvent(event);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextUtil.applicationContext = applicationContext;
    }
}
