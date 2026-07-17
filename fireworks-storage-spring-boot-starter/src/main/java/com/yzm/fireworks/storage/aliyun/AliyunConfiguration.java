package com.yzm.fireworks.storage.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.yzm.fireworks.storage.api.DirectUploadService;
import com.yzm.fireworks.storage.api.StorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@ConditionalOnClass(OSS.class)
@ConditionalOnProperty(prefix = "fireworks.storage", name = "provider", havingValue = "aliyun")
public class AliyunConfiguration {

    @Bean
    @ConditionalOnMissingBean(OSS.class)
    public OSS ossClient(StorageProperties properties) {
        StorageProperties.Aliyun aliyun = properties.getAliyun();
        Assert.notNull(aliyun, "fireworks.storage.aliyun 配置不能为空");
        Assert.hasText(aliyun.getEndpoint(), "fireworks.storage.aliyun.endpoint 不能为空");
        Assert.hasText(aliyun.getAccessKey(), "fireworks.storage.aliyun.access-key 不能为空");
        Assert.hasText(aliyun.getSecretKey(), "fireworks.storage.aliyun.secret-key 不能为空");
        return new OSSClientBuilder().build(
                aliyun.getEndpoint(),
                aliyun.getAccessKey(),
                aliyun.getSecretKey());
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService aliyunStorageService(StorageProperties properties, OSS ossClient) {
        return new AliyunStorageService(properties, ossClient);
    }

    @Bean
    @ConditionalOnMissingBean(DirectUploadService.class)
    public DirectUploadService aliyunDirectUploadService(OSS ossClient, StorageProperties properties) {
        return new AliyunDirectUploadService(ossClient, properties);
    }
}
