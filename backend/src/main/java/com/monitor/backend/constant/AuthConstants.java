package com.monitor.backend.constant;

/**
 * 认证相关常量
 */
public final class AuthConstants {
    private AuthConstants() {}
    
    // JWT相关
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    
    // 加密相关
    public static final String ENCRYPTION_PREFIX = "ENC:";
    
    // 链路追踪
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 判断是否为Bearer Token
     */
    public static boolean isBearerToken(String authHeader) {
        return authHeader != null && authHeader.startsWith(BEARER_PREFIX);
    }
    
    /**
     * 从Authorization头提取Token
     */
    public static String extractToken(String authHeader) {
        if (isBearerToken(authHeader)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
    
    /**
     * 判断是否为加密数据
     */
    public static boolean isEncrypted(String data) {
        return data != null && data.startsWith(ENCRYPTION_PREFIX);
    }
}
