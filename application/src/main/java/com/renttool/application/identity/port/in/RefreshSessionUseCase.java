package com.renttool.application.identity.port.in;

import java.time.Instant;

public interface RefreshSessionUseCase {
  /**
   * Emite un token nuevo para una sesión vigente, conservando su inicio. Falla si la cuenta ya no
   * está activa o si la sesión superó su duración máxima.
   */
  AuthenticatedSession refresh(Long accountId, Instant authenticatedAt);
}
