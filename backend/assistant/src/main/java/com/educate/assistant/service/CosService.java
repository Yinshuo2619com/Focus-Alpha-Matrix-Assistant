package com.educate.assistant.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class CosService {

    @Value("${cos.secret-id:}")
    private String secretId;

    @Value("${cos.secret-key:}")
    private String secretKey;

    @Value("${cos.bucket-name:}")
    private String bucketName;

    @Value("${cos.region:}")
    private String region;

    @Value("${cos.url-prefix:}")
    private String urlPrefix;

    private COSClient cosClient;
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (secretId == null || secretId.isEmpty()) {
            enabled = false;
            return;
        }
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        cosClient = new COSClient(credentials, clientConfig);
        enabled = true;
    }

    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String uploadFile(MultipartFile file, String objectKey) throws IOException {
        if (!enabled) throw new RuntimeException("COS 未配置");
        PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, file.getInputStream(), null);
        cosClient.putObject(request);
        return urlPrefix + "/" + objectKey;
    }

    public String uploadBytes(byte[] data, String objectKey) {
        if (!enabled) throw new RuntimeException("COS 未配置");
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
        PutObjectRequest request = new PutObjectRequest(bucketName, objectKey, bis, null);
        cosClient.putObject(request);
        return urlPrefix + "/" + objectKey;
    }

    public void deleteFile(String objectKey) {
        if (!enabled) return;
        cosClient.deleteObject(bucketName, objectKey);
    }

    public byte[] downloadFile(String objectKey) {
        if (!enabled) throw new RuntimeException("COS 未配置");
        COSObject cosObject = cosClient.getObject(new GetObjectRequest(bucketName, objectKey));
        try (java.io.InputStream is = cosObject.getObjectContent()) {
            return is.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    public String getContentType(String objectKey) {
        if (!enabled) return "application/octet-stream";
        ObjectMetadata metadata = cosClient.getObjectMetadata(bucketName, objectKey);
        return metadata.getContentType();
    }

    public String extractObjectKey(String url) {
        if (url == null || !url.startsWith(urlPrefix)) return null;
        return url.substring(urlPrefix.length() + 1);
    }
}
