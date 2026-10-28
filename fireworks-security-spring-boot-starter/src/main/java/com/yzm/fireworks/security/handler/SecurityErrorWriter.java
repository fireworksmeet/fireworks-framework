package com.yzm.fireworks.security.handler;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证 / 授权失败的统一响应写出器。
 *
 * <p>401、403、500 三处（EntryPoint、AccessDeniedHandler、认证过滤器内部错误）都委托给它，
 * 避免各自的 JSON 格式与状态码漂移。
 *
 * <p><b>为什么不注入容器的 {@code ObjectMapper}</b>：那是业务可改的全局实例
 * （命名策略、null 包含策略、模块注册都可能被定制），会让 401/403 的字段名随业务配置漂移，
 * 而 {@link Result} 的契约是固定的小驼峰。这里使用一份固定配置的 writer，
 * 只保留 {@code NON_NULL}（省略 {@code data} / {@code error} 空字段，与全局默认一致）。
 *
 * @author JYuan
 */
@Slf4j
public class SecurityErrorWriter {

    private static final ObjectWriter WRITER = JsonMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build()
            .writer();

    /**
     * 写出统一 JSON 响应。
     *
     * @param response   当前响应，不为 {@code null}
     * @param httpStatus HTTP 状态码（401 / 403 / 500）
     * @param error      业务错误码与消息
     */
    public void write(HttpServletResponse response, int httpStatus, ExceptionStatus error) {
        try {
            response.setStatus(httpStatus);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(WRITER.writeValueAsString(Result.error(error)));
        } catch (IOException e) {
            // 响应已提交（客户端断开等）时不再抛出，避免污染请求处理线程
            log.error("写出认证失败响应出错, status={}", httpStatus, e);
        }
    }
}
