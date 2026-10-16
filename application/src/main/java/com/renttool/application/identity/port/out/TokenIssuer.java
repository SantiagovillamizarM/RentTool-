package com.renttool.application.identity.port.out;

import com.renttool.domain.identity.Account;
import java.time.Instant;

public interface TokenIssuer {
  /** {@code authenticatedAt} es el inicio de sesión original; se conserva al renovar. */
  String issue(Account account, Instant authenticatedAt, Instant expiresAt);
}
