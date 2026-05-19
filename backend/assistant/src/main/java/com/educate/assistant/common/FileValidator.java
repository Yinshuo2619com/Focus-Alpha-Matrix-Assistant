package com.educate.assistant.common;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
//import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FileValidator {

    private static final Map<String, String> IMAGE_MAGIC_NUMBERS = new HashMap<>();

    static {
        IMAGE_MAGIC_NUMBERS.put("FFD8FF", "jpg");
        IMAGE_MAGIC_NUMBERS.put("89504E47", "png");
        IMAGE_MAGIC_NUMBERS.put("47494638", "gif");
    }

    public static String getRealImageType(MultipartFile file) throws IOException {
        // 读取整个文件到内存缓冲区保证可重复读取
        byte[] fileContent = file.getBytes();
        
        // 读取前8字节做魔数校验
        if (fileContent.length < 4) {
            return null;
        }
        
        byte[] header = new byte[8];
        System.arraycopy(fileContent, 0, header, 0, Math.min(8, fileContent.length));

        String hex = bytesToHex(header).toUpperCase();

        for (Map.Entry<String, String> entry : IMAGE_MAGIC_NUMBERS.entrySet()) {
            if (hex.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
}