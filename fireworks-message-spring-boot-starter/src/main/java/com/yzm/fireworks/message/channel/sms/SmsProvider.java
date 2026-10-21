package com.yzm.fireworks.message.channel.sms;

import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.api.MessageProvider;

/**
 * 短信渠道 SPI。
 *
 * @author JYuan
 */
public interface SmsProvider extends MessageProvider<SmsMessage> {

    @Override
    default ChannelType channelType() {
        return ChannelType.SMS;
    }
}
