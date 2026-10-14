package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.out.TokenIssuer;
import com.renttool.domain.identity.Account;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
class JwtTokenIssuer implements TokenIssuer {
  static final String ISSUER = "renttool";
  static final String ROLE_CLAIM = "role";
  private static final Duration VALIDITY = Duration.ofHours(1);

  private final JwtEncoder encoder;

  JwtTokenIssuer(JwtEncoder encoder) {
    this.encoder = encoder;
  }

  @Override
  public String issue(Account account) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(account.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(VALIDITY))
            .claim(ROLE_CLAIM, account.role().name())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
