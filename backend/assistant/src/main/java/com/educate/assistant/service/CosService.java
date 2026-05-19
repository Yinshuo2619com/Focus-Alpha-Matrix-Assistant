package com.educate.assistant.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.PutObjectRequest;
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

    public void deleteFile(String objectKey) {
        if (!enabled) return;
        cosClient.deleteObject(bucketName, objectKey);
    }

    public String extractObjectKey(String url) {
        if (url == null || !url.startsWith(urlPrefix)) return null;
        return url.substring(urlPrefix.length() + 1);
    }
}
