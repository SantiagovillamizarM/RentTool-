package com.renttool.infrastructure.identity.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;

@Configuration
public class JwtConfig {
  @Bean
  SecretKeySpec jwtKey(@Value("${renttool.jwt-secret}") String secret) {
    if (secret.length() < 32)
      throw new IllegalArgumentException("JWT_SECRET requiere al menos 32 caracteres");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec jwtKey) {
    return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(jwtKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec jwtKey) {
    var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
    // Tolerancia de reloj corta: el token del administrador dura 10 minutos y debe caducar a tiempo.
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(Duration.ofSeconds(5)),
            new JwtIssuerValidator(JwtTokenIssuer.ISSUER)));
    return decoder;
  }

  /** Traduce el claim de rol del token a autoridades ROLE_* de Spring Security. */
  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName(JwtTokenIssuer.ROLE_CLAIM);
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return converter;
  }
}
