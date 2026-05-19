package com.educate.assistant.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class JwtBlacklistService {

    private static final String PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public JwtBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将 token 加入黑名单，TTL 设为 token 剩余过期时间
     * Redis 不可用时静默忽略
     */
    public void blacklist(String token, long expirationMillis) {
        if (expirationMillis <= 0) return;
        try {
            redisTemplate.opsForValue().set(PREFIX + token, "1", expirationMillis, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    /**
     * 检查 token 是否在黑名单中
     * Redis 不可用时返回 false，不阻塞正常请求
     */
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
        } catch (Exception e) {
            return false;
        }
    }
}
