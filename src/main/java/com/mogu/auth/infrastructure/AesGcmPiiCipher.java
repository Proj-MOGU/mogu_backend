package com.mogu.auth.infrastructure;

import com.mogu.auth.application.PiiCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmPiiCipher implements PiiCipher {
    private final Map<String, SecretKeySpec> keys;
    private final String activeVersion;
    private final SecureRandom random = new SecureRandom();

    public AesGcmPiiCipher(Map<String, byte[]> keys, String activeVersion) {
        var copied = new HashMap<String, SecretKeySpec>();
        keys.forEach((version, key) -> {
            if (version == null || !version.matches("[0-9]+") || key == null || key.length != 32) throw failure();
            copied.put(version, new SecretKeySpec(key, "AES"));
        });
        if (activeVersion == null || !copied.containsKey(activeVersion)) throw failure();
        this.keys = Map.copyOf(copied);
        this.activeVersion = activeVersion;
    }

    @Override
    public String encrypt(String plaintext, String context) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeVersion), new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return "v" + activeVersion + "." + encoder.encodeToString(iv) + "."
                    + encoder.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException | RuntimeException ignored) {
            throw failure();
        }
    }

    @Override
    public String decrypt(String ciphertext, String context) {
        try {
            if (ciphertext == null || !ciphertext.matches("v[0-9]+\\.[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]+"))
                throw failure();
            String[] parts = ciphertext.split("\\.");
            var key = keys.get(parts[0].substring(1));
            if (key == null) throw failure();
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            if (encrypted.length < 16
                    || !Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(encrypted)
                            .equals(parts[2])) throw failure();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException ignored) {
            throw failure();
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException("PII cipher operation failed");
    }
}
