package com.yzm.fireworks.storage;

import com.yzm.fireworks.common.constants.StringPool;
import com.yzm.fireworks.storage.service.S3StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 存储模块自动装配：基于 AWS S3 SDK v2，统一对接所有 S3 协议兼容存储。
 * <p>
 * 不再区分厂商：MinIO / SeaweedFS / RustFS / Ceph RGW / 阿里云 OSS（兼容模式）/ AWS S3 共用同一份代码。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "fireworks.storage", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnClass(S3Client.class)
public class StorageAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(S3Client.class)
    public S3Client s3Client(StorageProperties properties) {
        Assert.hasText(properties.getEndpoint(), "fireworks.storage.endpoint 不能为空");
        Assert.hasText(properties.getAccessKey(), "fireworks.storage.access-key 不能为空");
        Assert.hasText(properties.getSecretKey(), "fireworks.storage.secret-key 不能为空");

        S3Client client = S3Client.builder()
                .endpointOverride(buildEndpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        // 关闭 aws-chunked 传输编码：阿里云 OSS 等实现不支持该编码，开启时会报
                        // "aws-chunked encoding is not supported with the specified x-amz-content-sha256 value"。
                        // 本模块的上传路径全部提供确定的 Content-Length（未知长度会先缓冲/落盘），因此关闭后无功能损失。
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
        log.info("Storage S3Client 已装配, endpoint={}, region={}, pathStyle={}",
                buildEndpoint(properties), properties.getRegion(), properties.isPathStyleAccess());
        return client;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(S3Presigner.class)
    public S3Presigner s3Presigner(StorageProperties properties) {
        Assert.hasText(properties.getEndpoint(), "fireworks.storage.endpoint 不能为空");
        Assert.hasText(properties.getAccessKey(), "fireworks.storage.access-key 不能为空");
        Assert.hasText(properties.getSecretKey(), "fireworks.storage.secret-key 不能为空");

        S3Presigner signer = S3Presigner.builder()
                .endpointOverride(buildEndpoint(properties))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .build();
        log.info("Storage S3Presigner 已装配");
        return signer;
    }

    @Bean
    @ConditionalOnMissingBean(S3StorageService.class)
    public S3StorageService s3StorageService(S3Client s3, S3Presigner presigner, StorageProperties properties) {
        return new S3StorageService(s3, presigner, properties);
    }

    private static URI buildEndpoint(StorageProperties properties) {
        String endpoint = properties.getEndpoint();
        if (endpoint.startsWith(StringPool.HTTP_HOST_PREFIX) || endpoint.startsWith(StringPool.HTTPS_HOST_PREFIX)) {
            return URI.create(endpoint);
        }
        return URI.create((properties.isSecure() ? StringPool.HTTPS_HOST_PREFIX : StringPool.HTTP_HOST_PREFIX) + endpoint);
    }
}