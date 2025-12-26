package com.monitor.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 前端传输加密服务
 * 用于解密前端加密传输的敏感数据（密码等）
 * 使用 AES-CBC 算法（与前端 crypto-js 兼容）
 */
@Service
public class TransmitEncryptionService {
    
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    
    @Value("${encryption.transmit-key:busi-monitor-transmit-key-32ch}")
    private String transmitKey;
    
    /**
     * 获取传输密钥（返回给前端）
     * 密钥基于固定配置，前端使用相同密钥加密
     */
    public String getTransmitKey() {
        return transmitKey;
    }
    
    /**
     * 解密前端传来的数据
     * 前端使用 crypto-js 的 AES.encrypt 加密
     * 格式: Base64(IV + EncryptedData)
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return null;
        }
        
        // 如果数据不是加密格式（不含特定前缀或太短），原样返回（向后兼容）
        if (!encryptedData.startsWith("ENC:")) {
            return encryptedData;
        }
        
        try {
            // 移除前缀
            String actualData = encryptedData.substring(4);
            byte[] combined = Base64.getDecoder().decode(actualData);
            
            // 提取 IV（前16字节）和密文
            byte[] iv = Arrays.copyOfRange(combined, 0, 16);
            byte[] encrypted = Arrays.copyOfRange(combined, 16, combined.length);
            
            // 生成256位密钥
            byte[] keyBytes = getKeyBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败，可能是旧数据，原样返回
            return encryptedData;
        }
    }
    
    /**
     * 加密数据（用于测试）
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return null;
        }
        try {
            byte[] keyBytes = getKeyBytes();
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            
            // 生成随机 IV
            byte[] iv = new byte[16];
            new java.security.SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            
            return "ENC:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
    
    private byte[] getKeyBytes() throws Exception {
        // 使用 SHA-256 将密钥转换为32字节
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(transmitKey.getBytes(StandardCharsets.UTF_8));
    }
}
