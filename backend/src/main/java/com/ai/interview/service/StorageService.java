package com.ai.interview.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储封装。
 * 只处理二进制读写，不含业务逻辑。
 */
@Service
@Slf4j
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-resume}")
    private String resumeBucket;

    @Value("${minio.bucket-audio}")
    private String audioBucket;

    public StorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * 上传文件到指定桶，返回对象路径（不含桶名）。
     */
    public String upload(String bucket, String objectName, MultipartFile file) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            log.info("Uploaded {} to bucket {} as {}", file.getOriginalFilename(), bucket, objectName);
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + objectName, e);
        }
    }

    /**
     * 下载对象为 InputStream，调用方负责关闭。
     */
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + objectName, e);
        }
    }

    /**
     * 生成预签名 URL，有效期 1 小时（供前端直接访问 PDF）。
     */
    public String generatePresignedUrl(String bucket, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL for: " + objectName, e);
        }
    }

    public String getResumeBucket() {
        return resumeBucket;
    }

    public String getAudioBucket() {
        return audioBucket;
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure bucket exists: " + bucket, e);
        }
    }
}
