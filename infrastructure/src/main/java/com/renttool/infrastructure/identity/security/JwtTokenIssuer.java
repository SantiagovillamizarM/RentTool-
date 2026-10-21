package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.out.TokenIssuer;
import com.renttool.domain.identity.Account;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
class JwtTokenIssuer implements TokenIssuer {
  static final String ISSUER = "renttool";
  static final String ROLE_CLAIM = "role";
  /** Inicio de sesión original (claim estándar de OpenID Connect); se conserva al renovar. */
  static final String AUTH_TIME_CLAIM = "auth_time";

  private final JwtEncoder encoder;
  /** El mismo reloj que calcula el vencimiento, para que la emisión nunca quede después de él. */
  private final Clock clock;

  JwtTokenIssuer(JwtEncoder encoder, Clock clock) {
    this.encoder = encoder;
    this.clock = clock;
  }

  @Override
  public String issue(Account account, Instant authenticatedAt, Instant expiresAt) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(account.id().toString())
            .issuedAt(clock.instant())
            .expiresAt(expiresAt)
            .claim(ROLE_CLAIM, account.role().name())
            .claim(AUTH_TIME_CLAIM, authenticatedAt.getEpochSecond())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
