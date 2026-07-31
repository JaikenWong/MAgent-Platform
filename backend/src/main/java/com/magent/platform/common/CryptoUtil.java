package com.magent.platform.common;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import com.magent.platform.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CryptoUtil {

    private static String aesKey;
    private static final String ALGO = "AES/GCM/NoPadding";

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
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(enc);
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
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] dec = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException("decrypt failed");
        }
    }
}