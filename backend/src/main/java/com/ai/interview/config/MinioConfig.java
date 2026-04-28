package com.ai.interview.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端 Bean 配置。
 * <p>连接参数（endpoint / access-key / secret-key）由 application.yml 中 minio.* 配置项提供。
 * {@link MinioClient} 是线程安全的单例，直接注入 {@link com.ai.interview.service.StorageService}。
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    /**
     * 构建并注册 MinIO 客户端 Bean，整个应用共享同一实例。
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
