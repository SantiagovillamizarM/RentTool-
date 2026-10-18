package com.renttool.application.identity.port.in;

import com.renttool.application.identity.port.in.ManageProvidersUseCase.ProfileCommand;
import com.renttool.domain.identity.Account;
import com.renttool.domain.identity.Role;
import java.util.Optional;

/** Alta de cuentas desde el sistema (arranque, datos demo), no desde el registro público. */
public interface ProvisionAccountUseCase {
  void provisionAdministrator(String email, String rawPassword);

  Account ensureAccount(String name, String email, String rawPassword, Role role);

  /**
   * Proveedor con sus datos legales y una contraseña propia (no temporal). Si ya existe, lo
   * devuelve sin cambiarlo.
   */
  Account ensureProvider(String email, String rawPassword, ProfileCommand profile);

  Optional<Account> findAccount(String email);

  /** Baja definitiva de una cuenta de sistema: no vuelve a entrar y conserva su historial. */
  void removeAccount(String email);
}
