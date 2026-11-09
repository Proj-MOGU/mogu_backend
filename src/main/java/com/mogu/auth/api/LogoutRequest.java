package com.mogu.auth.api;

public record LogoutRequest(String refreshToken) {
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
}
