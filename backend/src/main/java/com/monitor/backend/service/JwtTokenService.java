package com.monitor.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT Token 服务
 * 用于生成和验证JWT Token
 */
@Service
public class JwtTokenService {
    
    @Value("${jwt.secret-key:busi-monitor-jwt-secret-key-must-be-256bits}")
    private String secretKeyStr;
    
    @Value("${jwt.expiration-hours:24}")
    private int expirationHours;
    
    /**
     * 生成JWT Token
     */
    public String generateToken(String username, String role) {
        SecretKey key = getSecretKey();
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationHours * 60 * 60 * 1000L);
        
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }
    
    /**
     * 验证Token并返回用户名
     * @return 用户名，如果Token无效则返回null
     */
    public String validateTokenAndGetUsername(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查Token是否有效
     */
    public boolean isTokenValid(String token) {
        return validateTokenAndGetUsername(token) != null;
    }
    
    private SecretKey getSecretKey() {
        // 确保密钥至少256位（32字节）
        byte[] keyBytes = new byte[32];
        byte[] secretBytes = secretKeyStr.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 32));
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
