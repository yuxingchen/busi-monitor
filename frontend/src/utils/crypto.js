/**
 * 前端传输加密工具
 * 使用 AES-CBC 加密敏感数据（密码等）
 */
import CryptoJS from 'crypto-js'

// 传输加密密钥（与后端配置保持一致）
const TRANSMIT_KEY = 'busi-monitor-transmit-2025-secure'

/**
 * 加密密码
 * @param {string} password - 明文密码
 * @returns {string} 加密后的密码（格式: ENC:Base64(IV+加密数据)）
 */
export function encryptPassword(password) {
    if (!password) return password
    
    try {
        // 生成随机 IV (16 bytes)
        const iv = CryptoJS.lib.WordArray.random(16)
        
        // 使用 SHA-256 生成 32 字节密钥（与后端一致）
        const key = CryptoJS.SHA256(TRANSMIT_KEY)
        
        // AES-CBC 加密
        const encrypted = CryptoJS.AES.encrypt(password, key, {
            iv: iv,
            mode: CryptoJS.mode.CBC,
            padding: CryptoJS.pad.Pkcs7
        })
        
        // 拼接 IV + 密文
        const combined = iv.concat(encrypted.ciphertext)
        
        // Base64 编码并添加前缀
        return 'ENC:' + CryptoJS.enc.Base64.stringify(combined)
    } catch (e) {
        console.error('加密失败:', e)
        return password
    }
}

/**
 * 检查是否已加密
 * @param {string} data - 数据
 * @returns {boolean} 是否已加密
 */
export function isEncrypted(data) {
    return data && data.startsWith('ENC:')
}

export default {
    encryptPassword,
    isEncrypted
}
