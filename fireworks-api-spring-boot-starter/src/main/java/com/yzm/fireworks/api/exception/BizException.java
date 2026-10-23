package com.yzm.fireworks.api.exception;


import com.yzm.fireworks.api.Result;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author JYuan
 * 业务异常
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BizException extends RuntimeException {

    private ExceptionStatus status;
    private Result<?> result;

    public BizException(ExceptionStatus status) {
        super(status.getMessage());
        this.status = status;
    }


    public BizException(Result<?> result) {
        super(result.getMessage());
        this.result = result;
        this.status = new ExceptionStatus() {
            @Override
            public int getCode() {
                return result.getCode();
            }

            @Override
            public String getMessage() {
                return result.getMessage();
            }
        };
    }


    public BizException(ExceptionStatus status, String msg) {
        super(msg);
        this.status = status;
    }

    /**
     * 保留底层异常：{@link #fillInStackTrace()} 只影响本异常的栈，cause 自己的栈仍然完整，
     * 排障时能看到真正失败的位置（如唯一键冲突的 SQL）。
     */
    public BizException(ExceptionStatus status, Throwable cause) {
        super(status.getMessage(), cause);
        this.status = status;
    }


    /**
     * 不打印堆栈，提高性能
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
