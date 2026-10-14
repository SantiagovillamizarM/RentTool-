package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public interface GetProfileUseCase {
  /** Devuelve la cuenta si existe y está activa; si no, falla la autenticación. */
  Account getActive(Long accountId);
}
