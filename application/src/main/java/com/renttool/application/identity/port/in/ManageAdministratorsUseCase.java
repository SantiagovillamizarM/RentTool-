package com.renttool.application.identity.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;

/**
 * Alta de administradores por otro administrador. El nuevo tiene los mismos permisos y recibe una
 * contraseña temporal que debe cambiar al entrar.
 */
public interface ManageAdministratorsUseCase {
  /** Administradores no eliminados, del más reciente al más antiguo. */
  PageResult<Account> list(Long adminId, PageQuery page);

  Account register(Long adminId, RegisterCommand command);

  record RegisterCommand(String name, String email, String temporaryPassword) {}
}
