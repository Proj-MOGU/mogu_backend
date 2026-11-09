package com.mogu.auth.application;

public final class PiiMasking {
    private PiiMasking() {}

    public static String phoneNumber(String phone) {
        if (phone == null || !phone.matches("[0-9]{10,11}")) return null;
        return phone.substring(0, 3)
                + (phone.length() == 11 ? "-****-" : "-***-")
                + phone.substring(phone.length() - 4);
    }

    public static String email(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at < 1 || at == email.length() - 1) return null;
        return email.substring(0, at >= 3 ? 2 : 1) + "***" + email.substring(at);
    }
}
