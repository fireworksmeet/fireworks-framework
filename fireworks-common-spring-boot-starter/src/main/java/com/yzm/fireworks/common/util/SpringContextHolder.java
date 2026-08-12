package com.yzm.fireworks.common.util;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Spring 应用上下文持有者与工具类
 *
 * @author JYuan
 */
@Slf4j
@Component
public class SpringContextHolder implements ApplicationContextAware, DisposableBean {

    /**
     * -- GETTER --
     *  获取 ApplicationContext
     */
    @Getter
    private static ApplicationContext applicationContext;

    /**
     * 检查 ApplicationContext 是否已成功初始化
     */
    public static boolean isInitialized() {
        return applicationContext != null;
    }

    // ─────────────────────────────────────────────────────────────
    //  1. Bean 获取（空安全，找不到或未初始化时优雅返回 null，不抛异常）
    // ─────────────────────────────────────────────────────────────

    /**
     * 根据 Class 类型获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        if (!isInitialized() || clazz == null) {
            return null;
        }
        try {
            return getApplicationContext().getBean(clazz);
        } catch (BeansException e) {
            log.debug("[SpringContextHolder] 容器中未找到类型为 [{}] 的 Bean", clazz.getName());
            return null;
        }
    }

    /**
     * 根据名称获取 Bean
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        if (!isInitialized() || !StringUtils.hasText(name)) {
            return null;
        }
        try {
            return (T) getApplicationContext().getBean(name);
        } catch (BeansException e) {
            log.debug("[SpringContextHolder] 容器中未找到名称为 [{}] 的 Bean", name);
            return null;
        }
    }

    /**
     * 根据名称和 Class 类型获取 Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        if (!isInitialized() || !StringUtils.hasText(name) || clazz == null) {
            return null;
        }
        try {
            return getApplicationContext().getBean(name, clazz);
        } catch (BeansException e) {
            log.debug("[SpringContextHolder] 容器中未找到名称为 [{}] 且类型为 [{}] 的 Bean", name, clazz.getName());
            return null;
        }
    }

    /**
     * 获取指定类型的 Bean 集合（返回 Map<beanName, beanInstance>）
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        if (!isInitialized() || clazz == null) {
            return Collections.emptyMap();
        }
        try {
            return getApplicationContext().getBeansOfType(clazz);
        } catch (BeansException e) {
            return Collections.emptyMap();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  2. 环境变量（Environment）配置读取
    // ─────────────────────────────────────────────────────────────

    /**
     * 读取配置属性（如 spring.profiles.active）
     */
    public static String getProperty(String key) {
        if (!isInitialized()) {
            return null;
        }
        return getApplicationContext().getEnvironment().getProperty(key);
    }

    /**
     * 读取配置属性（带默认值）
     */
    public static String getProperty(String key, String defaultValue) {
        if (!isInitialized()) {
            return defaultValue;
        }
        return getApplicationContext().getEnvironment().getProperty(key, defaultValue);
    }

    /**
     * 获取当前激活的 Profile 列表
     */
    public static String[] getActiveProfiles() {
        if (!isInitialized()) {
            return new String[0];
        }
        return getApplicationContext().getEnvironment().getActiveProfiles();
    }

    // ─────────────────────────────────────────────────────────────
    //  3. 动态 Bean 注册与事件发布
    // ─────────────────────────────────────────────────────────────

    /**
     * 动态注册 Bean（自动推导首字母小写的类名作为 Bean 名称）
     */
    public static <T> void registerBean(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Class 不能为 null");
        String beanName = decapitalize(clazz.getSimpleName());
        registerBean(beanName, clazz, null);
    }

    /**
     * 动态注册 Bean
     */
    public static <T> void registerBean(String beanName, Class<T> clazz, Map<String, Object> propertyValues, Object... constructorArgs) {
        Assert.hasText(beanName, "beanName 不能为空");
        Objects.requireNonNull(clazz, "Class 不能为 null");
        if (!isInitialized()) {
            log.warn("[SpringContextHolder] 容器未初始化，跳过 Bean 注册: {}", beanName);
            return;
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (!ObjectUtils.isEmpty(constructorArgs)) {
            for (Object constructorArg : constructorArgs) {
                builder.addConstructorArgValue(constructorArg);
            }
        }
        if (!ObjectUtils.isEmpty(propertyValues)) {
            propertyValues.forEach((key, val) -> {
                if (StringUtils.hasText(key)) {
                    builder.addPropertyValue(key, val);
                }
            });
        }

        AbstractBeanDefinition rawBeanDefinition = builder.getRawBeanDefinition();
        AutowireCapableBeanFactory beanFactory = getApplicationContext().getAutowireCapableBeanFactory();
        if (beanFactory instanceof DefaultListableBeanFactory defaultListableBeanFactory) {
            if (defaultListableBeanFactory.containsBeanDefinition(beanName)) {
                defaultListableBeanFactory.removeBeanDefinition(beanName);
            }
            defaultListableBeanFactory.registerBeanDefinition(beanName, rawBeanDefinition);
            log.info("[SpringContextHolder] 动态注册 Bean 成功: {}", beanName);
        }
    }

    /**
     * 发布 Spring 事件
     */
    public static void publishEvent(Object event) {
        if (isInitialized() && event != null) {
            getApplicationContext().publishEvent(event);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  4. 生命周期感知与清理
    // ─────────────────────────────────────────────────────────────

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        SpringContextHolder.applicationContext = context;
    }

    @Override
    public void destroy() {
        SpringContextHolder.clearHolder();
    }

    public static void clearHolder() {
        if (log.isDebugEnabled()) {
            log.debug("[SpringContextHolder] 清理 ApplicationContext: {}", applicationContext);
        }
        applicationContext = null;
    }

    private static String decapitalize(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}