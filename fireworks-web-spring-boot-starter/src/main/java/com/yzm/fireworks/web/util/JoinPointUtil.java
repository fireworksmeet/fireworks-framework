package com.yzm.fireworks.web.util;

import com.yzm.fireworks.common.annotation.Sensitive;
import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.common.enums.SensitiveType;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author JYuan
 */
public class JoinPointUtil {

    private static final DefaultParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 获取当前方法所在类
     */
    public static Class<?> getClazz(JoinPoint joinPoint) {
        return joinPoint.getTarget().getClass();
    }

    /**
     * 获取当前方法反射类
     * <p>
     * 注意：此方法通过 {@link MethodSignature} 获取，在接口代理场景下返回接口方法，
     * 在实现类直接代理场景下返回实现类方法。
     * 若需要读取参数注解，请使用 {@link #getParamAnnotations(JoinPoint)} 以覆盖全部场景。
     */
    public static Method getMethod(JoinPoint joinPoint) {
        return ((MethodSignature) joinPoint.getSignature()).getMethod();
    }

    /**
     * 获取当前方法的参数值
     */
    public static Object[] getParamValues(JoinPoint joinPoint) {
        return joinPoint.getArgs();
    }

    /**
     * 获取当前方法的参数名
     */
    public static String[] getParamNames(Method method) {
        return PARAMETER_NAME_DISCOVERER.getParameterNames(method);
    }

    /**
     * 获取当前方法上的注解
     */
    public static <T extends Annotation> T getMethodAnnotation(Method method, Class<T> annotation) {
        return AnnotationUtils.findAnnotation(method, annotation);
    }

    /**
     * 获取当前方法所在类上的注解
     */
    public static <T extends Annotation> T getClassAnnotation(Class<?> tClazz, Class<T> annotation) {
        return AnnotationUtils.findAnnotation(tClazz, annotation);
    }

    /**
     * 获取方法参数的注解二维数组，同时覆盖接口方法和实现类方法两个来源。
     *
     * <p><b>问题背景</b>：{@code joinPoint.getSignature().getMethod()} 在不同代理方式下行为不同：
     * <ul>
     *   <li>JDK 动态代理（实现了接口）：返回<b>接口方法</b>，读不到实现类参数上的注解</li>
     *   <li>CGLIB 代理（无接口）：返回<b>实现类方法</b>，读不到接口参数上的注解</li>
     * </ul>
     * 因此需要同时读取两个来源并合并，才能保证注解不丢失。
     *
     * <p><b>合并策略</b>：收集接口方法和实现类方法两处的参数注解，去重合并。
     *
     * @param joinPoint 当前切点
     * @return 合并后的参数注解二维数组，第一维为参数下标，第二维为该参数上的注解列表
     */
    public static Annotation[][] getParamAnnotations(JoinPoint joinPoint) {
        Method signatureMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        
        // 收集所有可能存在注解的方法来源
        List<Annotation[][]> allAnnotations = new ArrayList<>();
        
        // 1. signature 方法（JDK代理时是接口方法，CGLIB时是实现类方法）
        allAnnotations.add(signatureMethod.getParameterAnnotations());
        
        // 2. 尝试从目标实现类获取方法（JDK代理时需要，CGLIB时可能和signatureMethod相同）
        try {
            Method targetMethod = targetClass.getMethod(
                    signatureMethod.getName(), signatureMethod.getParameterTypes());
            if (!targetMethod.equals(signatureMethod)) {
                allAnnotations.add(targetMethod.getParameterAnnotations());
            }
        } catch (NoSuchMethodException ignored) {
        }
        
        // 3. 对于CGLIB代理，还需要查找接口方法上的注解
        // signatureMethod是实现类方法时，需要检查其实现的接口
        if (!signatureMethod.getDeclaringClass().isInterface()) {
            for (Class<?> iface : targetClass.getInterfaces()) {
                try {
                    Method interfaceMethod = iface.getMethod(
                            signatureMethod.getName(), signatureMethod.getParameterTypes());
                    allAnnotations.add(interfaceMethod.getParameterAnnotations());
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        
        // 合并所有来源的注解
        return mergeParamAnnotations(allAnnotations, signatureMethod.getParameterCount());
    }
    
    /**
     * 合并多个来源的参数注解数组
     */
    private static Annotation[][] mergeParamAnnotations(List<Annotation[][]> allAnnotations, int paramCount) {
        if (allAnnotations.size() == 1) {
            return allAnnotations.getFirst();
        }
        
        Annotation[][] merged = new Annotation[paramCount][];
        
        for (int i = 0; i < paramCount; i++) {
            List<Annotation> mergedList = new ArrayList<>();
            
            for (Annotation[][] annotations : allAnnotations) {
                if (i < annotations.length) {
                    for (Annotation annotation : annotations[i]) {
                        // 检查是否已存在相同类型的注解
                        boolean alreadyPresent = mergedList.stream()
                                .anyMatch(a -> a.annotationType() == annotation.annotationType());
                        if (!alreadyPresent) {
                            mergedList.add(annotation);
                        }
                    }
                }
            }
            
            merged[i] = mergedList.toArray(new Annotation[0]);
        }
        
        return merged;
    }

    /**
     * 从单个参数的注解数组中查找 {@link Sensitive}，找不到返回 null
     *
     * @param annotations 参数的注解数组
     * @return Sensitive 注解，如果未找到则返回 null
     */
    public static Sensitive findSensitiveAnnotation(Annotation[] annotations) {
        if (ObjectUtils.isEmpty(annotations)) {
            return null;
        }
        for (Annotation annotation : annotations) {
            if (annotation instanceof Sensitive sensitive) {
                return sensitive;
            }
        }
        return null;
    }

    /**
     * 对字符串应用脱敏处理
     *
     * <p>与 {@link com.yzm.fireworks.common.sensitive.SensitiveSerializer} 保持一致的脱敏逻辑：
     * 遍历 sensitiveTypes，找到第一个 match() 匹配的类型进行脱敏；若无匹配则使用第一个类型兜底。
     *
     * @param str            原始字符串
     * @param sensitiveTypes 脱敏类型数组
     * @return 脱敏后的字符串
     */
    public static String applySensitive(String str, SensitiveType[] sensitiveTypes) {
        if (str == null || ObjectUtils.isEmpty(sensitiveTypes)) {
            return str;
        }
        for (SensitiveType type : sensitiveTypes) {
            if (type.match(str)) {
                return type.process(str);
            }
        }
        return sensitiveTypes[0].process(str);
    }

    /**
     * 对方法参数数组进行脱敏处理
     *
     * @param paramValues      参数值数组
     * @param paramAnnotations 参数注解二维数组
     * @return 脱敏后的参数值数组（注意：不修改原数组，创建新数组）
     */
    public static Object[] processParams(Object[] paramValues, Annotation[][] paramAnnotations) {
        if (ObjectUtils.isEmpty(paramValues)) {
            return paramValues;
        }

        Object[] processedValues = new Object[paramValues.length];
        for (int i = 0; i < paramValues.length; i++) {
            Object paramValue = paramValues[i];
            if (ObjectUtils.isEmpty(paramValue)) {
                processedValues[i] = paramValue;
                continue;
            }

            // 特殊处理：MultipartFile、InputStream、Reader 等流类型
            Object processedValue = processSpecialParamType(paramValue);
            if (processedValue != null) {
                processedValues[i] = processedValue;
                continue;
            }

            // 获取参数注解
            Annotation[] annotations = null;
            if (!ObjectUtils.isEmpty(paramAnnotations) && i < paramAnnotations.length) {
                annotations = paramAnnotations[i];
            }

            // 检查是否有 @Sensitive 注解
            Sensitive sensitive = findSensitiveAnnotation(annotations);
            if (sensitive != null && paramValue instanceof String str) {
                processedValues[i] = applySensitive(str, sensitive.value());
            } else {
                processedValues[i] = paramValue;
            }
        }

        return processedValues;
    }

    /**
     * 处理特殊参数类型（MultipartFile、InputStream、Reader 等）
     *
     * @param paramValue 参数值
     * @return 处理后的值，如果不需要特殊处理则返回 null
     */
    private static Object processSpecialParamType(Object paramValue) {
        if (paramValue instanceof MultipartFile file) {
            // MultipartFile 转换为文件大小和文件名
            if (!file.isEmpty()) {
                return "[MultipartFile: size=" + file.getSize() + StringPool.COMMA + " filename=" + file.getOriginalFilename() + "]";
            } else {
                return "[MultipartFile: empty]";
            }
        } else if (paramValue instanceof InputStream) {
            // InputStream 不应该被序列化
            return "[InputStream]";
        } else if (paramValue instanceof Reader) {
            // Reader 不应该被序列化
            return "[Reader]";
        }
        return null;
    }

    /**
     * 对方法参数数组进行脱敏处理，构建为 LinkedHashMap
     *
     * @param paramNames       参数名数组
     * @param paramValues      参数值数组
     * @param paramAnnotations 参数注解二维数组
     * @return 脱敏后的参数 Map
     */
    public static Map<String, Object> processParamsToMap(String[] paramNames, Object[] paramValues, Annotation[][] paramAnnotations) {
        if (ObjectUtils.isEmpty(paramNames) || ObjectUtils.isEmpty(paramValues)
                || paramNames.length != paramValues.length) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            Object paramValue = paramValues[i];
            if (ObjectUtils.isEmpty(paramValue)) {
                paramMap.put(paramNames[i], paramValue);
                continue;
            }

            // 特殊处理：MultipartFile、InputStream、Reader 等流类型
            Object processedValue = processSpecialParamType(paramValue);
            if (processedValue != null) {
                paramMap.put(paramNames[i], processedValue);
                continue;
            }

            // 获取参数注解
            Annotation[] annotations = null;
            if (!ObjectUtils.isEmpty(paramAnnotations) && i < paramAnnotations.length) {
                annotations = paramAnnotations[i];
            }

            // 检查是否有 @Sensitive 注解
            Sensitive sensitive = findSensitiveAnnotation(annotations);
            if (sensitive != null && paramValue instanceof String str) {
                paramMap.put(paramNames[i], applySensitive(str, sensitive.value()));
            } else {
                paramMap.put(paramNames[i], paramValue);
            }
        }

        return paramMap;
    }
}