package com.yzm.fireworks.message.channel.realtime;

import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.api.MessageProvider;

/**
 * 实时渠道 SPI。
 *
 * @author JYuan
 */
public interface RealtimeProvider extends MessageProvider<RealtimeMessage> {

    @Override
    default ChannelType channelType() {
        return ChannelType.REALTIME;
    }
}
