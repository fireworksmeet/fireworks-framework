package com.yzm.fireworks.msg.utils;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 消息去重工具类
 *
 * <p>修复：新增 {@link #generateByMessageId(String)} 方法，
 * 当业务方提供了 messageId 时直接以其为唯一 key，避免冗余拼接 content/platform。
 *
 * @author JYuan
 */
public class DeduplicationUtil {

    /**
     * 以 messageId 直接生成去重 key（推荐，messageId 语义上已唯一）
     */
    public static String generateByMessageId(String messageId) {
        return DigestUtils.sha256Hex(("msgId:" + messageId).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成消息去重 key（单用户）
     */
    public static String generateDeduplicationKey(String userId, String content, String platform) {
        String rawKey = String.format("%s:%s:%s",
                userId != null ? userId : "broadcast",
                content,
                platform != null ? platform : "default");
        return DigestUtils.sha256Hex(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成消息去重 key（群组消息）
     */
    public static String generateGroupDeduplicationKey(String groupId, String content, String platform) {
        String rawKey = String.format("group:%s:%s:%s",
                groupId,
                content,
                platform != null ? platform : "default");
        return DigestUtils.sha256Hex(rawKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成批量消息去重 key（多收件人，对收件人排序后拼接，确保相同集合生成相同 key）
     */
    public static String generateBatchDeduplicationKey(String[] recipients, String content, String platform) {
        String[] sorted = Arrays.copyOf(recipients, recipients.length);
        Arrays.sort(sorted);
        String recipientsStr = String.join(",", sorted);
        String rawKey = String.format("batch:%s:%s:%s",
                recipientsStr,
                content,
                platform != null ? platform : "default");
        return DigestUtils.sha256Hex(rawKey.getBytes(StandardCharsets.UTF_8));
    }
}