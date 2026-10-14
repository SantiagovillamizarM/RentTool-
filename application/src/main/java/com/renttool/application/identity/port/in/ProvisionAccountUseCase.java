package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;

/** Alta de cuentas desde el sistema (arranque, datos demo), no desde el registro público. */
public interface ProvisionAccountUseCase {
  void provisionAdministrator(String email, String rawPassword);

  Account ensureAccount(String name, String email, String rawPassword, Role role);
}
