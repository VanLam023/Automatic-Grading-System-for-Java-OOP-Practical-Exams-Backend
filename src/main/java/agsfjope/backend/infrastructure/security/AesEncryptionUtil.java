package agsfjope.backend.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utility for AES-256-GCM encryption/decryption of sensitive system configuration values.
 */
@Component
public class AesEncryptionUtil {

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    @Value("${app.encryption.secret-key:change-this-secret-key-in-production}")
    private String secretKey;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt plaintext using AES-256-GCM.
     *
     * @param plainText raw plaintext
     * @return base64 encoded ciphertext with IV prefix
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(buildAesKey(), "AES"), gcmParameterSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể mã hóa dữ liệu cấu hình", ex);
        }
    }

    /**
     * Decrypt AES-256-GCM ciphertext.
     *
     * @param cipherText base64 encoded value with IV prefix
     * @return raw plaintext
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return "";
        }

        try {
            byte[] allBytes = Base64.getDecoder().decode(cipherText);
            if (allBytes.length <= IV_LENGTH_BYTES) {
                return "";
            }

            byte[] iv = Arrays.copyOfRange(allBytes, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(allBytes, IV_LENGTH_BYTES, allBytes.length);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(buildAesKey(), "AES"), gcmParameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể giải mã dữ liệu cấu hình", ex);
        }
    }

    /**
     * Mask sensitive value, showing only last 4 characters.
     *
     * @param value input sensitive value
     * @return masked value
     */
    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return "****";
        }

        return "****" + normalized.substring(normalized.length() - 4);
    }

    private byte[] buildAesKey() {
        try {
            // Derive fixed 32-byte key from secret string using SHA-256.
            return MessageDigest.getInstance("SHA-256")
                    .digest(secretKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể khởi tạo khóa mã hóa AES", ex);
        }
    }
}
