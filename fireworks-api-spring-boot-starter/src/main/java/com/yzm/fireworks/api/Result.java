package com.yzm.fireworks.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.CommonExceptionStatus;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import com.yzm.fireworks.common.util.SpringContextHolder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.yzm.fireworks.common.constants.StringPool.UNKNOWN;


/**
 * @author JYuan
 * 统一响应结果
 * <p>
 * 【契约】只承载 {@code code / data / message / system} 四段信息：
 * <ul>
 *   <li>失败原因由 {@code code}（精确语义）+ {@code message}（面向用户的提示）表达；</li>
 *   <li>刻意不提供结构化的 {@code error} 载荷：框架默认开启校验 fail-fast，一次请求只可能有一个失败原因，
 *       列表形态无从发挥；而 {@code Object} 类型的载荷又是无法约束的隐式契约，跨服务时客户端只能猜结构。
 *       业务上下文（如库存余量）应放进 {@code data} 或通过专用错误码表达。</li>
 * </ul>
 * <p>
 * 【刻意不重写 {@code toString()}】需要 JSON 日志时由调用方用 {@code JsonUtil} 显式序列化。
 * 框架在此处持有一个 {@code ObjectMapper} 依赖，只为日志服务，收益与耦合不成正比；且重写后一旦
 * 序列化失败或容器未就绪，降级路径反而可能把 {@code data} 中的敏感字段明文带进日志。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
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

    /**
     * 是否成功。
     * <p>
     * 必须保留 {@link JsonIgnore}：{@code isSuccess()} 符合 JavaBean 属性规范，Jackson 会把它识别为
     * {@code success} 属性，漏掉注解会让每个响应多出一个字段，违反 api.md 约定的响应结构。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return CommonExceptionStatus.SUCCESS.getCode() == this.code;
    }

    /**
     * 是否失败，理由与 {@link #isSuccess()} 相同，同样需要 {@link JsonIgnore}。
     */
    @JsonIgnore
    public boolean isFailure() {
        return !isSuccess();
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

    public static <R> Result<R> error(ExceptionStatus status) {
        Result<R> rst = new Result<>();
        rst.setCode(status.getCode());
        rst.setMessage(status.getMessage());
        rst.setSystem(getApplicationName());
        return rst;
    }

    public static <R> Result<R> error(int code, String message) {
        return error(code, message, getApplicationName());
    }

    public static <R> Result<R> error(int code, String message, String system) {
        Result<R> rst = new Result<>();
        rst.setCode(code);
        rst.setMessage(message);
        rst.setSystem(null == system ? getApplicationName() : system);
        return rst;
    }

    /**
     * 用另一个结果填充错误信息，用于跨服务透传（{@code code} / {@code message} / {@code system} 保持不变）。
     */
    public static <R> Result<R> error(Result<?> result) {
        Result<R> rst = new Result<>();
        rst.setCode(result.getCode());
        rst.setMessage(result.getMessage());
        rst.setSystem(result.getSystem() == null ? getApplicationName() : result.getSystem());
        return rst;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 获取应用名称，Spring 容器未就绪时返回 "UNKNOWN"，避免 NPE。
     */
    private static String getApplicationName() {
        return SpringContextHolder.getProperty("spring.application.name", UNKNOWN);
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
        if (isFailure()) {
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
        if (isFailure()) {
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
     *   if (orderResult.isFailure()) {
     *       return orderResult.propagate();
     *   }
     * }</pre>
     *
     * @param <R> 目标泛型
     * @return 携带原始错误信息的 Result，code/message/system 均保持不变
     * @throws IllegalStateException 若在成功状态下调用（success 状态无需传播错误）
     */
    @JsonIgnore
    public <R> Result<R> propagate() {
        if (isSuccess()) {
            throw new IllegalStateException("不能对成功的 Result 调用 propagate()，请先用 isFailure() 判断");
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
        if (isSuccess()) {
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
        if (isFailure()) {
            consumer.accept(this);
        }
        return this;
    }

    // ==================== 数据转换 ====================

    /**
     * 将 data 转换为另一种类型，返回新的 Result 对象。
     */
    public <R> Result<R> convert(Function<T, R> mapper) {
        if (isFailure()) {
            // 失败时直接传播错误，无需转换 data
            return propagate();
        }
        Result<R> rst = new Result<>();
        rst.setCode(this.code);
        rst.setMessage(this.message);
        rst.setSystem(this.system);
        rst.setData(this.data == null ? null : mapper.apply(this.data));
        return rst;
    }

}
