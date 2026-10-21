package com.yzm.fireworks.message.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构化接收者。
 * <p>
 * 不用 {@code String[]}：避免「邮件消息里带手机号」这类无效字段。各渠道只读取自己关心的字段，
 * 其余字段被忽略。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class Recipient {

    /**
     * 实时推送：频道 ID 列表；REALTIME 必填。
     * <p>
     * 含义完全由业务定义（用户频道、群组频道、广播频道都可以），框架只负责按上限分批并用逗号拼接。
     * 元素不能包含逗号（Nchan 频道分隔符）与空白。
     */
    private List<String> channelIds;

    /**
     * 短信：手机号列表；SMS 必填，E.164 或国内 11 位。
     */
    private List<String> phoneNumbers;

    /**
     * 邮件：收件人地址列表；EMAIL 必填。
     */
    private List<String> emailAddresses;
}
