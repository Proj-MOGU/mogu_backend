package com.mogu.auth.application;

public interface PiiCipher {
    String encrypt(String plaintext, String context);

    String decrypt(String ciphertext, String context);
}
