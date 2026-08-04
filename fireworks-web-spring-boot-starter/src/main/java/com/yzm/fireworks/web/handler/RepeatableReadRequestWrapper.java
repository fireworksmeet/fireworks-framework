package com.yzm.fireworks.web.handler;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 生产级可重复读取的 HttpServletRequest 包装类 (JDK 17 / Spring Boot 3+)
 *
 * <p>基于 JDK 9+ 原生 readNBytes 实现防 OOM 截断与高效流缓存。
 */
@Slf4j
@Getter
public class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 默认最大可缓存 Body 大小：2MB（防止超大 JSON 或恶意请求撑爆 JVM 堆内存）
     */
    private static final int DEFAULT_MAX_BODY_SIZE = 1024 * 1024 * 2;

    private final byte[] body;
    private final boolean truncated;

    public RepeatableReadRequestWrapper(HttpServletRequest request) throws IOException {
        this(request, DEFAULT_MAX_BODY_SIZE);
    }

    public RepeatableReadRequestWrapper(HttpServletRequest request, int maxBodySize) throws IOException {
        super(request);

        int contentLength = request.getContentLength();

        // 1. OOM 防御：如果 Content-Length 明确超过最大限制，不进行内存强读
        if (contentLength > maxBodySize) {
            this.body = new byte[0];
            this.truncated = true;
            log.warn("Request body size [{}] exceeds max log cache limit [{}]. Truncated.", contentLength, maxBodySize);
            return;
        }

        InputStream is = request.getInputStream();
        // 1. JDK 9+ 原生防 OOM 读取：最多读取 maxBodySize 个字节
        this.body = is.readNBytes(maxBodySize);

        // 2. 尝试再读 1 个字节，若能读到，说明真实 Body 长度超出了 maxBodySize 限制
        this.truncated = (is.read() != -1);
    }

    @Override
    public ServletInputStream getInputStream() {
        return new ResettableServletInputStream(this.body);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        // 使用请求指定的字符集，若无指定则模式 UTF-8
        String encoding = getCharacterEncoding();
        Charset charset = (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    /**
     * Servlet 3.1+ 规范兼容的流重置实现
     */
    private static class ResettableServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        public ResettableServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener != null) {
                try {
                    readListener.onDataAvailable();
                    if (isFinished()) {
                        readListener.onAllDataRead();
                    }
                } catch (IOException e) {
                    readListener.onError(e);
                }
            }
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public int read(@NonNull byte[] b, int off, int len) {
            return buffer.read(b, off, len);
        }

        @Override
        public int available() {
            return buffer.available();
        }
    }
}