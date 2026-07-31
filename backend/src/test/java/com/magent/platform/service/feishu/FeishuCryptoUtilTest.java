package com.magent.platform.service.feishu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class FeishuCryptoUtilTest {

    private FeishuCryptoUtil crypto;
    private static final String ENCRYPT_KEY = "test-encrypt-key-12345";

    @BeforeEach
    void setup() {
        crypto = new FeishuCryptoUtil();
    }

    @Test
    void decrypt_roundTrip_returnsOriginalMessage() throws Exception {
        String originalJson = "{\"type\":\"url_verification\",\"challenge\":\"abc123\"}";
        String encrypted = feishuEncrypt(ENCRYPT_KEY, originalJson);

        String decrypted = crypto.decrypt(ENCRYPT_KEY, encrypted);

        assertThat(decrypted).isEqualTo(originalJson);
    }

    @Test
    void decrypt_eventPayload() throws Exception {
        String eventJson = """
            {"schema":"2.0","header":{"event_type":"im.message.receive_v1","token":"verify-token"},
             "event":{"message":{"msg_type":"text","chat_id":"oc123","content":"{\\"text\\":\\"hello\\"}"}}}""";
        String encrypted = feishuEncrypt(ENCRYPT_KEY, eventJson);

        String decrypted = crypto.decrypt(ENCRYPT_KEY, encrypted);

        assertThat(decrypted).contains("im.message.receive_v1");
        assertThat(decrypted).contains("hello");
    }

    @Test
    void verifySignature_validSignature_returnsTrue() throws Exception {
        String timestamp = "1700000000";
        String nonce = "nonce-abc";
        String body = "{\"encrypt\":\"some-data\"}";

        String sig = computeSignature(ENCRYPT_KEY, timestamp, nonce, body);

        boolean valid = crypto.verifySignature(ENCRYPT_KEY, timestamp, nonce, body, sig);
        assertThat(valid).isTrue();
    }

    @Test
    void verifySignature_wrongSignature_returnsFalse() {
        boolean valid = crypto.verifySignature(ENCRYPT_KEY, "123", "nonce", "body", "wrong-sig");
        assertThat(valid).isFalse();
    }

    @Test
    void verifySignature_nullSignature_returnsFalse() {
        boolean valid = crypto.verifySignature(ENCRYPT_KEY, "123", "nonce", "body", null);
        assertThat(valid).isFalse();
    }

    // ───── helper: encrypt using feishu's scheme ─────

    private String feishuEncrypt(String encryptKey, String message) throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(encryptKey.getBytes(StandardCharsets.UTF_8));

        // plaintext = random(16) + msg_len(4 big-endian) + msg + PKCS7Padding
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] lengthBytes = ByteBuffer.allocate(4).putInt(msgBytes.length).array();

        int totalLen = 16 + 4 + msgBytes.length;
        int padLen = 16 - (totalLen % 16);
        if (padLen == 0) padLen = 16;
        byte[] padding = new byte[padLen];
        for (int i = 0; i < padLen; i++) padding[i] = (byte) padLen;

        byte[] plaintext = new byte[totalLen + padLen];
        System.arraycopy(random, 0, plaintext, 0, 16);
        System.arraycopy(lengthBytes, 0, plaintext, 16, 4);
        System.arraycopy(msgBytes, 0, plaintext, 20, msgBytes.length);
        System.arraycopy(padding, 0, plaintext, totalLen, padLen);

        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(keyBytes, 0, 16);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String computeSignature(String encryptKey, String timestamp, String nonce, String body)
            throws Exception {
        String toSign = timestamp + nonce + encryptKey + body;
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(toSign.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}