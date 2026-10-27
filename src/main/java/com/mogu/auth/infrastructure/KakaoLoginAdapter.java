package com.mogu.auth.infrastructure;

import com.mogu.auth.domain.AuthProvider;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class KakaoLoginAdapter extends OidcSocialLoginAdapter {
    public KakaoLoginAdapter(
            OidcProviderProperties properties, @Qualifier("authClock") Clock clock, Environment environment) {
        super(AuthProvider.KAKAO, properties.kakao(), clock, environment);
    }
}
