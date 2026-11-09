package com.mogu.auth.support;

import com.mogu.auth.infrastructure.AesGcmPiiCipher;
import java.util.Map;

public final class PiiTestKeys {
    private PiiTestKeys() {}

    public static AesGcmPiiCipher cipher() {
        return new AesGcmPiiCipher(Map.of("1", new byte[32]), "1");
    }
}
