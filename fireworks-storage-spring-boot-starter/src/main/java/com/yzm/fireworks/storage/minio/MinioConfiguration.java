package com.yzm.fireworks.storage.minio;

import com.yzm.fireworks.storage.api.DirectUploadService;
import com.yzm.fireworks.storage.api.StorageService;
import com.yzm.fireworks.storage.core.StorageProperties;
import com.yzm.fireworks.storage.core.StorageUrlUtils;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "fireworks.storage", name = "provider", havingValue = "minio", matchIfMissing = true)
public class MinioConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(StorageProperties properties) {
        StorageProperties.Minio minio = properties.getMinio();
        Assert.notNull(minio, "fireworks.storage.minio 配置不能为空");
        Assert.hasText(minio.getEndpoint(), "fireworks.storage.minio.endpoint 不能为空");
        Assert.hasText(minio.getAccessKey(), "fireworks.storage.minio.access-key 不能为空");
        Assert.hasText(minio.getSecretKey(), "fireworks.storage.minio.secret-key 不能为空");
        return MinioClient.builder()
                .endpoint(StorageUrlUtils.buildEndpointUrl(minio.getEndpoint(), minio.isSecure()))
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    /**
     * 专门用于生成预签名 URL 的 MinioClient。
     * <p>
     * MinIO 使用 SigV4 签名，Host 强制纳入签名范围——如果用内网 endpoint 签名后再替换 host，
     * 客户端请求到来时 MinIO 会用实际收到的 Host 重新计算签名，导致 403。
     * 因此，生成预签名 URL 时必须直接用公网地址构建 client，让签名从一开始就包含正确的 Host。
     * <p>
     * 配置了 {@code fireworks.storage.public-endpoint} 时使用公网地址；否则退化为与 {@link #minioClient}
     * 相同的内网地址（相当于两个 client 等价，不影响原有行为）。
     * <p>
     * 注意：该 client <b>仅用于预签名</b>，不用于实际上传/下载操作（桶操作需要走内网保证性能和安全）。
     */
    @Bean("presignMinioClient")
    public MinioClient presignMinioClient(StorageProperties properties) {
        StorageProperties.Minio minio = properties.getMinio();
        String endpoint = StringUtils.hasText(properties.getPublicEndpoint())
                ? properties.getPublicEndpoint()
                : StorageUrlUtils.buildEndpointUrl(minio.getEndpoint(), minio.isSecure());
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService minioStorageService(StorageProperties properties, MinioClient minioClient,
            @Qualifier("presignMinioClient") MinioClient presignMinioClient) {
        return new MinioStorageService(properties, minioClient, presignMinioClient);
    }

    @Bean
    @ConditionalOnMissingBean(DirectUploadService.class)
    public DirectUploadService minioDirectUploadService(MinioClient minioClient,
            @Qualifier("presignMinioClient") MinioClient presignMinioClient,
            StorageProperties properties) {
        return new MinioDirectUploadService(minioClient, presignMinioClient, properties);
    }
}