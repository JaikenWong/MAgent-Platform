package com.magent.platform.service.feishu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 飞书事件订阅加密/签名工具.
 *
 * 飞书 AES-256-CBC 方案:
 *   key = SHA256(encrypt_key) → 32 bytes
 *   iv  = key 的前 16 bytes
 *   明文 = random(16) + msg_len(4, big-endian) + msg + PKCS7Padding
 *
 * 签名:
 *   sig = SHA256(timestamp + nonce + encrypt_key + body)
 */
@Slf4j
@Component
public class FeishuCryptoUtil {

    private static final String AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding";

    /**
     * 解密飞书加密的事件 payload.
     * @param encryptKey 飞书 Encrypt Key (明文, 从 Bot 配置解密后得到)
     * @param encrypted  Base64 编码的密文 (body 中 "encrypt" 字段的值)
     * @return 解密后的 JSON 字符串
     */
    public String decrypt(String encryptKey, String encrypted) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(encryptKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(keyBytes, 0, 16);

            Cipher cipher = Cipher.getInstance(AES_CBC_PKCS5);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));

            // 跳过前 16 字节 random + 4 字节长度, 取实际消息
            if (decrypted.length < 20) {
                throw new IllegalArgumentException("decrypted payload too short");
            }
            int msgLen = readIntBigEndian(decrypted, 16);
            int msgStart = 20;
            if (msgStart + msgLen > decrypted.length) {
                msgLen = decrypted.length - msgStart;
            }
            return new String(decrypted, msgStart, msgLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("feishu decrypt failed", e);
            throw new IllegalArgumentException("decrypt failed: " + e.getMessage());
        }
    }

    /**
     * 校验飞书请求签名.
     * @param encryptKey 飞书 Encrypt Key (明文)
     * @param timestamp  X-Lark-Request-Timestamp header
     * @param nonce       X-Lark-Request-Nonce header
     * @param body        原始请求 body 字符串
     * @param signature   X-Lark-Signature header
     * @return true if signature matches
     */
    public boolean verifySignature(String encryptKey, String timestamp, String nonce,
                                   String body, String signature) {
        if (signature == null || signature.isBlank()) return false;
        try {
            String toSign = timestamp + nonce + encryptKey + body;
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(toSign.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.warn("signature verification failed", e);
            return false;
        }
    }

    private int readIntBigEndian(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             | (data[offset + 3] & 0xFF);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}