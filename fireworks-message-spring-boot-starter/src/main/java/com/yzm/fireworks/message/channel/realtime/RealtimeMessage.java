package com.yzm.fireworks.message.channel.realtime;

import com.yzm.fireworks.message.domain.ChannelMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实时渠道入参。
 * <p>
 * 框架不建模"平台 / 用户 / 群组"等频道语义：{@link #path} 由业务给出，{@link #channelIds} 是频道 ID 列表，
 * 框架只负责拼接与分批。
 *
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class RealtimeMessage implements ChannelMessage {

    /**
     * 消息标识；由门面从 {@code MessageEnvelope} 复制，写入消息体供客户端去重。
     */
    private String messageId;

    /**
     * publish 路径；必填。绝对 URL（http / https）直接使用，相对路径拼在 {@code fireworks.message.channels.realtime.nchan.url} 之后。
     * <p>
     * 频道 ID 会以 {@code channel-id-delimiter}（默认 {@code ,}）拼接后追加在路径末尾，
     * 因此 path 应止于频道 ID 之前，例如 {@code /pub/ios/user} 或 {@code http://nchan:80/pub/broadcast}。
     */
    private String path;

    /**
     * 频道 ID 列表；必填，至少一个。
     * <p>
     * 含义由业务决定；框架按 {@code max-channels-per-publish} 与 {@code max-channel-path-bytes} 分批，
     * 每批一次 publish。元素不能包含逗号与空白。
     */
    private List<String> channelIds;

    /**
     * 消息标题；可空，原样写入消息体。
     */
    private String title;

    /**
     * 消息正文；可空，原样写入消息体。
     */
    private String content;

    /**
     * 业务载荷；序列化后写入消息体 data，门面取 {@code envelope.realtimeData}（任意对象，无需转 Map）。
     * <p>
     * 客户端路由类型、业务结构版本等语义一并放进这里（如 {@code params.type}、{@code params.version}），
     * 框架不定义，也不参与解析。
     */
    private Object data;
}
