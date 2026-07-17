package com.yzm.fireworks.msg.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yzm.fireworks.msg.core.message.*;
import com.yzm.fireworks.msg.entity.MessageRecord;
import com.yzm.fireworks.msg.enums.MessageStatus;
import com.yzm.fireworks.msg.mapper.MessageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

/**
 * 消息记录服务
 *
 * @author JYuan
 */
@Slf4j
@RequiredArgsConstructor
public class MessageRecordService {

    private final MessageRecordMapper messageRecordMapper;

    /**
     * 创建消息记录
     */
    public void createRecord(BaseMessage message, MessageStatus status) {
        MessageRecord record = new MessageRecord();
        record.setMessageId(message.getMessageId());
        record.setMessageType(message.getMessageType());
        record.setPriority(message.getPriority());
        record.setPlatform(message.getPlatform());

        // 根据消息类型提取接收者信息
        switch (message) {
            case WebSocketMessage wsMsg -> {
                if (!ObjectUtils.isEmpty(wsMsg.getUserIds())) {
                    record.setUserId(String.join(",", wsMsg.getUserIds()));
                }
                record.setGroupId(wsMsg.getGroupId());
            }
            case EmailMessage emailMsg -> {
                if (!ObjectUtils.isEmpty(emailMsg.getEmailTo())) {
                    record.setUserId(String.join(",", emailMsg.getEmailTo()));
                }
            }
            case SmsMessage smsMsg -> {
                if (!ObjectUtils.isEmpty(smsMsg.getPhoneNumbers())) {
                    record.setUserId(String.join(",", smsMsg.getPhoneNumbers()));
                }
            }
            default -> {
            }
        }

        record.setTitle(message.getTitle());
        record.setContent(message.getContent());
        record.setTemplateId(message.getTemplateId());
        record.setStatus(status);
        record.setRetryCount(0);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());

        messageRecordMapper.insert(record);
        log.debug("Message record created: messageId={}", message.getMessageId());
    }

    /**
     * 更新消息记录
     */
    public void updateRecord(MessageResult result) {
        LambdaUpdateWrapper<MessageRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MessageRecord::getMessageId, result.getMessageId())
                .set(MessageRecord::getStatus, result.getStatus())
                .set(MessageRecord::getErrorMessage, result.getErrorMessage())
                .set(MessageRecord::getExternalMessageId, result.getExternalMessageId())
                .set(MessageRecord::getRetryCount, result.getRetryCount())
                .set(MessageRecord::getSendTime, result.getSendTime())
                .set(MessageRecord::getUpdatedAt, LocalDateTime.now());

        int updated = messageRecordMapper.update(updateWrapper);
        if (updated > 0) {
            log.debug("Message record updated: messageId={}, status={}",
                    result.getMessageId(), result.getStatus());
        } else {
            log.warn("Message record not found for update: messageId={}, status={}",
                    result.getMessageId(), result.getStatus());
        }
    }

    /**
     * 增加重试次数（使用数据库原子操作，避免并发问题）
     */
    public void incrementRetryCount(String messageId) {
        LambdaUpdateWrapper<MessageRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(MessageRecord::getMessageId, messageId)
                .setSql("retry_count = retry_count + 1")
                .set(MessageRecord::getUpdatedAt, LocalDateTime.now());

        int updated = messageRecordMapper.update(updateWrapper);
        log.debug("Retry count incremented: messageId={}, updated={}", messageId, updated);
    }
}
