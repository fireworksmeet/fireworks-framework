package com.yzm.fireworks.message.channel.email;

import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.api.MessageProvider;

/**
 * 邮件渠道 SPI。
 *
 * @author JYuan
 */
public interface EmailProvider extends MessageProvider<EmailMessage> {

    @Override
    default ChannelType channelType() {
        return ChannelType.EMAIL;
    }
}
