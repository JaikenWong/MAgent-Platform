package com.magent.platform.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 字段加密工具.
 * 密文格式: Base64( IV[12] || ciphertext+tag ). 解密时前 12 字节为 IV.
 */
@Component
public class CryptoUtil {

    private static String aesKey;
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;

    @Value("${magent.crypto.aes-key}")
    public void setAesKey(String key) {
        CryptoUtil.aesKey = key;
    }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(aesKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");

            byte[] iv = new byte[GCM_IV_LEN];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            // iv + ciphertext(+tag) -> Base64
            byte[] combined = new byte[GCM_IV_LEN + enc.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LEN);
            System.arraycopy(enc, 0, combined, GCM_IV_LEN, enc.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new BizException("encrypt failed");
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(aesKey.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec key = new SecretKeySpec(Arrays.copyOf(keyBytes, 16), "AES");

            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length < GCM_IV_LEN + 1) {
                throw new BizException("decrypt failed: ciphertext too short");
            }

            byte[] iv = new byte[GCM_IV_LEN];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LEN);
            byte[] enc = new byte[combined.length - GCM_IV_LEN];
            System.arraycopy(combined, GCM_IV_LEN, enc, 0, enc.length);

            Cipher cipher = Cipher.getInstance(ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LEN, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            byte[] dec = cipher.doFinal(enc);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("decrypt failed");
        }
    }
}
