package com.renttool.application.identity.service;

import com.renttool.domain.identity.Account;
import java.time.Duration;
import java.time.Instant;

/**
 * Cuánto vive un token. El del administrador expira a los 10 minutos y se renueva solo mientras
 * hay actividad: si nadie lo usa, caduca también en el servidor aunque alguien lo haya copiado.
 * Ninguna sesión se renueva más allá de {@link #MAX_SESSION} desde el inicio de sesión.
 */
public final class SessionPolicy {
  public static final Duration ADMIN_IDLE_TIMEOUT = Duration.ofMinutes(10);
  public static final Duration DEFAULT_VALIDITY = Duration.ofHours(1);
  public static final Duration MAX_SESSION = Duration.ofHours(8);

  private SessionPolicy() {}

  static Duration validityFor(Account account) {
    return account.isAdministrator() ? ADMIN_IDLE_TIMEOUT : DEFAULT_VALIDITY;
  }

  /** Vencimiento del próximo token: lo que antes ocurra entre su vigencia y el tope de sesión. */
  static Instant expiryFor(Account account, Instant now, Instant authenticatedAt) {
    var byValidity = now.plus(validityFor(account));
    var bySession = authenticatedAt.plus(MAX_SESSION);
    return byValidity.isBefore(bySession) ? byValidity : bySession;
  }

  static boolean canRenew(Instant now, Instant authenticatedAt) {
    return authenticatedAt != null && now.isBefore(authenticatedAt.plus(MAX_SESSION));
  }
}
