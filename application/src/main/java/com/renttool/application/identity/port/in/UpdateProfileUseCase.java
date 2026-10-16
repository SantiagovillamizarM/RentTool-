package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public interface UpdateProfileUseCase {
  /** Cambia el nombre propio. El del administrador es fijo. */
  Account rename(Long accountId, String name);
}
