package com.mogu.auth.domain;

public interface RefreshTokenRepository {
    void save(RefreshToken token);
}
