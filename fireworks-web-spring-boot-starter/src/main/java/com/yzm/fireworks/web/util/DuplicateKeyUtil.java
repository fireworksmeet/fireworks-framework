package com.yzm.fireworks.web.util;

import com.yzm.fireworks.api.exception.BizException;
import com.yzm.fireworks.common.enums.ExceptionStatus;
import org.springframework.dao.DuplicateKeyException;

import java.util.function.Supplier;

/**
 * @author JYuan
 */
public class DuplicateKeyUtil {

    public static <T> T throwIfDuplicate(Supplier<T> supplier, ExceptionStatus status) {
        try {
            return supplier.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(status);
        }
    }

    public static <T> T handleIfDuplicate(Supplier<T> supplier) {
        return handleIfDuplicate(supplier, null);
    }

    public static <T> T handleIfDuplicate(Supplier<T> supplier, Runnable callback) {
        try {
            return supplier.get();
        } catch (DuplicateKeyException e) {
            if (callback != null) {
                callback.run();
            }
            return null;
        }
    }
}
