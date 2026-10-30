package com.mogu.auth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mogu.auth.di")
public record DiProperties(String pepper, boolean ephemeralPepperAllowed) {
    @Override
    public String toString() {
        return "DiProperties[pepper=***, ephemeralPepperAllowed=" + ephemeralPepperAllowed + "]";
    }
}
