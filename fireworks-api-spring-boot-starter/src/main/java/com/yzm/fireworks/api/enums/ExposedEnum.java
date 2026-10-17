package com.yzm.fireworks.api.enums;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记该枚举对外暴露（供下拉框、筛选条件、字典接口等场景使用）。
 * <p>
 * 【为什么用注解而不是接口】"有业务编码"与"要对外暴露"是两个正交维度：
 * 前者由 {@link CodeEnum} 保证，几乎所有业务枚举都需要；后者只适用于一部分枚举
 * （如内部流转状态不应出现在前端字典里）。用注解表达暴露，避免为此多造一个接口，
 * 也避免把"实现某接口"当作隐式的对外承诺。
 * <p>
 * 【框架只做声明与发现】被标注的枚举会被扫描并注册为 {@code List<Class<? extends CodeEnum<?>>>}，
 * 框架<b>不提供</b>字典的增删改、缓存、国际化等运行时能力——需要时由业务在扫描结果之上自行构建。
 * <p>
 * 被标注的类必须同时实现 {@link CodeEnum}，否则扫描时会跳过并打印警告。
 * <p>
 * 扫描由 {@code fireworks.exposed-enum.enabled=true} 开启，范围默认取启动类所在包，
 * 可用 {@code fireworks.exposed-enum.scan-packages} 覆盖。
 *
 * @author JYuan
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExposedEnum {
}
