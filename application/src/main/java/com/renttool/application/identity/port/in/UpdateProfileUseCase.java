package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;

public interface UpdateProfileUseCase {
  /** Cambia el nombre propio. El del administrador es fijo. */
  Account rename(Long accountId, String name);

  /** Teléfono de contacto del cliente, visible para los proveedores de sus reservas. */
  Account changePhone(Long accountId, String phone);
}
