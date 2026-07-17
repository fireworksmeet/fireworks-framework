package com.yzm.fireworks.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import com.yzm.fireworks.common.util.ApplicationContextUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.yzm.fireworks.common.constants.StringPool.*;


/**
 * @author JYuan
 * 统一响应结果
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Slf4j
public class Result<T> {

    /**
     * 业务码
     */
    @Schema(title = "业务码")
    @Setter
    protected int code;

    /**
     * 返回结果集
     */
    @Schema(title = "结果集")
    protected T data;

    /**
     * 错误结构化参数
     */
    @Schema(title = "错误结构化参数")
    @Setter
    protected Object error;

    /**
     * 错误信息
     */
    @Schema(title = "错误信息")
    @Setter
    protected String message;

    @Schema(title = "出错的系统")
    @Setter
    protected String system;

    protected Result() {
    }

    public Result<T> setData(T data) {
        this.data = data;
        return this;
    }

    @JsonIgnore
    public boolean success() {
        return CommonExceptionStatus.SUCCESS.getCode() == this.code;
    }

    @JsonIgnore
    public boolean fail() {
        return !success();
    }

    // ==================== 静态工厂方法 ====================

    public static <R> Result<R> ok() {
        return ok(null);
    }

    public static <R> Result<R> ok(R data) {
        Result<R> rst = new Result<>();
        rst.setCode(CommonExceptionStatus.SUCCESS.getCode());
        rst.setData(data);
        return rst;
    }

    public static <R, E> Result<R> error(E error) {
        return error(CommonExceptionStatus.SERVER_ERROR, error);
    }

    public static <R> Result<R> error(ExceptionStatus status) {
        return error(status, null);
    }

    public static <R, E> Result<R> error(ExceptionStatus status, E error) {
        Result<R> rst = new Result<>();
        rst.setCode(status.getCode());
        rst.setMessage(status.getMessage());
        rst.setError(error);
        rst.setSystem(getApplicationName());
        return rst;
    }

    public static <R, E> Result<R> error(Result<E> result) {
        Result<R> rst = new Result<>();
        rst.setCode(result.getCode());
        rst.setMessage(result.getMessage());
        rst.setError(result.getError());
        rst.setSystem(result.getSystem() == null ? getApplicationName() : result.getSystem());
        return rst;
    }

    public static <R> Result<R> error(int code, String message) {
        return error(code, message, null);
    }

    public static <R, E> Result<R> error(int code, String message, E error) {
        return error(code, message, error, getApplicationName());
    }

    public static <R, E> Result<R> error(int code, String message, E error, String system) {
        Result<R> rst = new Result<>();
        rst.setCode(code);
        rst.setMessage(message);
        rst.setError(error);
        rst.setSystem(null == system ? getApplicationName() : system);
        return rst;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 懒加载单例 ObjectMapper，优先从 Spring 容器获取，容器不可用时使用默认实例。
     * 避免每次调用都 new ObjectMapper() 带来的性能损耗和配置不一致问题。
     */
    private static volatile ObjectMapper fallbackObjectMapper;

    private static ObjectMapper getObjectMapper() {
        try {
            if (ApplicationContextUtil.getApplicationContext() != null) {
                return ApplicationContextUtil.getApplicationContext().getBean(ObjectMapper.class);
            }
        } catch (Exception ignored) {
            // Spring 容器未就绪或无法获取 Bean，降级使用默认实例
        }
        // 双重检查锁，保证 fallback 实例的线程安全
        if (fallbackObjectMapper == null) {
            synchronized (Result.class) {
                if (fallbackObjectMapper == null) {
                    fallbackObjectMapper = new ObjectMapper();
                }
            }
        }
        return fallbackObjectMapper;
    }

    /**
     * 获取应用名称，Spring 容器未就绪时返回 "unknown"，避免 NPE。
     */
    private static String getApplicationName() {
        try {
            return ApplicationContextUtil.getApplicationContext()
                    .getEnvironment()
                    .getProperty("spring.application.name", UNKNOWN);
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private <E> E convertObject(Object value, Class<E> eClass) {
        if (null == value) {
            return null;
        }
        return getObjectMapper().convertValue(value, eClass);
    }

    /**
     * 将 error 字段转换为指定类型。
     * <p>
     * 修复：原代码 error instanceof List 时调用 getErrors() 但未 return，
     * 导致结果被丢弃后继续尝试将 List 转为单个对象，引发转换异常。
     */
    public <E> E getError(Class<E> eClass) {
        if (null == error) {
            return null;
        }
        if (error instanceof List) {
            // error 是集合时，取第一个元素返回（与 getErrors 保持语义区分）
            List<E> errors = getErrors(eClass);
            return ObjectUtils.isEmpty(errors) ? null : errors.getFirst();
        }
        return convertObject(error, eClass);
    }

    @SuppressWarnings("rawtypes")
    public <E> List<E> getErrors(Class<E> eClass) {
        if (null == error) {
            return null;
        }
        List<E> result = new ArrayList<>();
        if (error instanceof List) {
            for (Object o : (List) error) {
                result.add(convertObject(o, eClass));
            }
        } else {
            result.add(convertObject(error, eClass));
        }

        return result;
    }

    /**
     * 从错误消息字符串解析 Result。
     */
    @SuppressWarnings("rawtypes")
    public static Result fromErrorMessage(String message) {
        Result result = error(CommonExceptionStatus.SERVER_ERROR);
        if (StringUtils.isEmpty(message)) {
            return result;
        } else if (message.contains(QUOTATION + CODE + QUOTATION + COLON)) {
            try {
                String trimmed = message.trim();
                if (trimmed.startsWith(LEFT_BRACE)) {
                    // JSON 对象
                    result = getObjectMapper().readValue(message, Result.class);
                } else if (trimmed.startsWith(LEFT_SQUARE_BRACKET)) {
                    // JSON 数组，取第一个元素
                    List<Result> results = getObjectMapper().readValue(message, new TypeReference<List<Result>>() {
                    });
                    if (results != null && !results.isEmpty()) {
                        result = results.getFirst();
                    }
                } else {
                    result = error(new ExceptionStatus() {
                        @Override
                        public int getCode() {
                            return CommonExceptionStatus.SERVER_ERROR.getCode();
                        }

                        @Override
                        public String getMessage() {
                            return message;
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("Failed to parse error data: {}", e.getMessage());
            }
        } else {
            result.setMessage(message);
        }
        if (result.getCode() == CommonExceptionStatus.SUCCESS.getCode()) {
            result.setCode(CommonExceptionStatus.SERVER_ERROR.getCode());
        }
        return result;
    }

    // ==================== 内部服务调用结果处理 ====================

    /**
     * 获取数据，若结果为失败则抛出 BizException（携带完整错误信息）。
     * <p>
     * 适用场景：服务 A 调用服务 B，需要在失败时快速终止并将 B 的错误传播到 A 的统一异常处理。
     * <pre>{@code
     *   UserDTO user = userService.getById(id).getDataOrThrow();
     * }</pre>
     *
     * @return data（保证非 null 时已通过校验）
     * @throws BizException 当结果为失败时
     */
    @JsonIgnore
    public T getDataOrThrow() {
        if (fail()) {
            throw new BizException(this);
        }
        return this.data;
    }

    /**
     * 获取数据，若结果为失败则执行 fallback 并返回其结果。
     * <p>
     * 适用场景：失败时需要降级处理而非抛出异常。
     * <pre>{@code
     *   UserDTO user = userService.getById(id).getDataOrElse(() -> defaultUser);
     * }</pre>
     *
     * @param fallback 失败时的降级逻辑
     * @return data 或 fallback 结果
     */
    public T getDataOrElse(Supplier<T> fallback) {
        if (fail()) {
            return fallback.get();
        }
        return this.data;
    }

    /**
     * 将当前失败结果传播为另一个类型的 Result，常用于方法返回值的错误透传。
     * <p>
     * 适用场景：服务 A 调用服务 B 失败后，需要将 B 的错误原样封装后返回给 A 的调用方。
     * <pre>{@code
     *   Result<OrderDTO> orderResult = orderService.create(req);
     *   if (orderResult.fail()) {
     *       return orderResult.propagate();
     *   }
     * }</pre>
     *
     * @param <R> 目标泛型
     * @return 携带原始错误信息的 Result，code/message/error/system 均保持不变
     * @throws IllegalStateException 若在成功状态下调用（success 状态无需传播错误）
     */
    @JsonIgnore
    public <R> Result<R> propagate() {
        if (success()) {
            throw new IllegalStateException("不能对成功的 Result 调用 propagate()，请先用 fail() 判断");
        }
        return Result.error(this);
    }

    /**
     * 成功时执行消费操作，失败时忽略（链式调用）。
     * <pre>{@code
     *   userService.getById(id)
     *       .onSuccess(user -> log.info("用户：{}", user.getName()))
     *       .onFailure(r -> log.warn("查询失败：{}", r.getMessage()));
     * }</pre>
     *
     * @param consumer 成功时对 data 的处理逻辑
     * @return this（支持链式调用）
     */
    public Result<T> onSuccess(Consumer<T> consumer) {
        if (success()) {
            consumer.accept(this.data);
        }
        return this;
    }

    /**
     * 失败时执行消费操作，成功时忽略（链式调用）。
     *
     * @param consumer 失败时对当前 Result 的处理逻辑
     * @return this（支持链式调用）
     */
    public Result<T> onFailure(Consumer<Result<T>> consumer) {
        if (fail()) {
            consumer.accept(this);
        }
        return this;
    }

    // ==================== 数据转换 ====================

    /**
     * 将 data 转换为另一种类型，返回新的 Result 对象。
     */
    public <R> Result<R> convert(Function<T, R> mapper) {
        if (fail()) {
            // 失败时直接传播错误，无需转换 data
            return propagate();
        }
        Result<R> rst = new Result<>();
        rst.setCode(this.code);
        rst.setMessage(this.message);
        rst.setError(this.error);
        rst.setSystem(this.system);
        rst.setData(this.data == null ? null : mapper.apply(this.data));
        return rst;
    }

    @Override
    public String toString() {
        return "Result{" + "code=" + code + ", data=" + data + ", error=" + error + ", message=" + message + ", system="
                + system + "}";
    }

}