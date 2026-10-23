package com.garmentfactory.shared.security;

import com.garmentfactory.shared.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HS256 access-token signing. The application fails to start if the secret is unusable. */
@Configuration
class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    @Bean
    SecretKey jwtSigningKey(AppProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.security().jwt().secret());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET must be base64 (generate one with: openssl rand -base64 48)", ex);
        }
        if (key.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, AppProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.security().jwt().issuer()));
        return decoder;
    }
}