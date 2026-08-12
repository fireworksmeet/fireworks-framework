package com.yzm.fireworks.storage.config;

import com.yzm.fireworks.storage.config.properties.StorageProperties;
import com.yzm.fireworks.storage.orphan.OrphanCleanupProperties;
import com.yzm.fireworks.storage.orphan.OrphanFileGuard;
import com.yzm.fireworks.storage.service.DirectUploadService;
import com.yzm.fireworks.storage.service.StorageService;
import com.yzm.fireworks.storage.service.minio.MinioDirectUploadServiceImpl;
import com.yzm.fireworks.storage.service.minio.MinioStorageServiceImpl;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Objects;

import static com.yzm.fireworks.common.constants.StringPool.*;

@Configuration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(prefix = "fireworks.storage", name = "provider", havingValue = "minio", matchIfMissing = true)
public class MinioConfiguration {

    private static final String PRESIGN_MINIO_CLIENT_NAME = "presignMinioClient";

    @Bean
    @Primary
    @ConditionalOnMissingBean(MinioClient.class)
    public MinioClient minioClient(StorageProperties properties) {
        StorageProperties.Minio minio = properties.getMinio();
        Objects.requireNonNull(minio, "fireworks.storage.minio 配置不能为空");
        Assert.hasText(minio.getEndpoint(), "fireworks.storage.minio.endpoint 不能为空");
        Assert.hasText(minio.getAccessKey(), "fireworks.storage.minio.access-key 不能为空");
        Assert.hasText(minio.getSecretKey(), "fireworks.storage.minio.secret-key 不能为空");
        return MinioClient.builder()
                .endpoint(buildEndpointUrl(minio.getEndpoint(), minio.isSecure()))
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
    @Bean(PRESIGN_MINIO_CLIENT_NAME)
    public MinioClient presignMinioClient(StorageProperties properties) {
        StorageProperties.Minio minio = properties.getMinio();
        String endpoint = StringUtils.hasText(properties.getPublicEndpoint())
                ? properties.getPublicEndpoint()
                : buildEndpointUrl(minio.getEndpoint(), minio.isSecure());
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService minioStorageService(StorageProperties properties, MinioClient minioClient,
                                              @Qualifier(PRESIGN_MINIO_CLIENT_NAME) MinioClient presignMinioClient) {
        return new MinioStorageServiceImpl(properties, minioClient, presignMinioClient);
    }

    @Bean
    @ConditionalOnMissingBean(DirectUploadService.class)
    public DirectUploadService minioDirectUploadService(@Qualifier(PRESIGN_MINIO_CLIENT_NAME) MinioClient presignMinioClient,
                                                        MinioStorageServiceImpl storageService,
                                                        @Autowired(required = false) OrphanFileGuard orphanFileGuard,
                                                        OrphanCleanupProperties orphanCleanupProperties) {
        return new MinioDirectUploadServiceImpl(presignMinioClient, storageService, orphanFileGuard, orphanCleanupProperties);
    }

    private String buildEndpointUrl(String endpoint, boolean secure) {
        if (endpoint.contains(HOST_PREFIX)) {
            return endpoint;
        }
        return (secure ? HTTPS_HOST_PREFIX : HTTP_HOST_PREFIX) + endpoint;
    }
}