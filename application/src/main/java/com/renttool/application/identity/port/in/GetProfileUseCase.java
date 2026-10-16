package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;
import java.util.Optional;

public interface GetProfileUseCase {
  /** Devuelve la cuenta si existe y está activa; si no, falla la autenticación. */
  Account getActive(Long accountId);

  /** Ubicación del logo si la cuenta es un proveedor que lo tiene. */
  Optional<String> logoOf(Long accountId);
}
