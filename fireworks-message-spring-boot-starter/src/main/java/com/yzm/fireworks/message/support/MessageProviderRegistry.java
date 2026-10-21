package com.yzm.fireworks.message.support;

import com.yzm.fireworks.message.api.MessageProvider;
import com.yzm.fireworks.message.domain.ChannelType;
import com.yzm.fireworks.message.domain.ProviderType;
import com.yzm.fireworks.message.properties.MessageProperties;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道实现注册表。
 * <p>
 * 启动时按渠道分组并完成实现选择：同一渠道多个实现且未指定 {@code provider} 时直接失败；
 * 选择结果一次算好，发送时只做查表，不重复判断。
 *
 * @author JYuan
 */
public final class MessageProviderRegistry {

    private final Map<ChannelType, MessageProvider<?>> selected = new EnumMap<>(ChannelType.class);

    public MessageProviderRegistry(List<MessageProvider<?>> providerList, MessageProperties properties) {
        Map<ChannelType, Map<ProviderType, MessageProvider<?>>> providers = group(providerList);
        select(providers, ChannelType.SMS, properties.getChannels().getSms().isEnabled(),
                properties.getChannels().getSms().getProvider(), "fireworks.message.channels.sms.provider");
        select(providers, ChannelType.EMAIL, properties.getChannels().getEmail().isEnabled(),
                null, "fireworks.message.channels.email.provider");
        select(providers, ChannelType.REALTIME, properties.getChannels().getRealtime().isEnabled(),
                null, "fireworks.message.channels.realtime.provider");
    }

    /**
     * 查找渠道实现。
     *
     * @param channelType 渠道类型
     * @return 实现
     * @throws IllegalStateException 渠道未启用或未注册实现
     */
    public MessageProvider<?> resolve(ChannelType channelType) {
        MessageProvider<?> provider = selected.get(channelType);
        if (provider == null) {
            throw new IllegalStateException("Channel " + channelType + " is not enabled");
        }
        return provider;
    }

    private Map<ChannelType, Map<ProviderType, MessageProvider<?>>> group(List<MessageProvider<?>> providerList) {
        Map<ChannelType, Map<ProviderType, MessageProvider<?>>> result = new EnumMap<>(ChannelType.class);
        for (MessageProvider<?> provider : providerList) {
            Map<ProviderType, MessageProvider<?>> byType =
                    result.computeIfAbsent(provider.channelType(), key -> new EnumMap<>(ProviderType.class));
            MessageProvider<?> previous = byType.putIfAbsent(provider.providerType(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate provider " + provider.providerType()
                        + " for channel " + provider.channelType());
            }
        }
        return result;
    }

    private void select(Map<ChannelType, Map<ProviderType, MessageProvider<?>>> providers,
                        ChannelType channelType, boolean enabled, ProviderType configured, String configPath) {
        if (!enabled) {
            return;
        }
        Map<ProviderType, MessageProvider<?>> byType = providers.get(channelType);
        if (byType == null || byType.isEmpty()) {
            throw new IllegalStateException("Channel " + channelType
                    + " is enabled but no MessageProvider is registered");
        }
        if (configured == null) {
            if (byType.size() > 1) {
                throw new IllegalStateException("Channel " + channelType + " has multiple providers "
                        + byType.keySet() + ", set " + configPath);
            }
            selected.put(channelType, byType.values().iterator().next());
            return;
        }
        MessageProvider<?> provider = byType.get(configured);
        if (provider == null) {
            throw new IllegalStateException("Configured provider " + configured + " for channel "
                    + channelType + " is not registered, available: " + byType.keySet());
        }
        selected.put(channelType, provider);
    }
}
