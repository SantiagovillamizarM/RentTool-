package com.renttool.application.identity.port.in;

import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import com.renttool.domain.identity.Account;

/**
 * Consulta y control de acceso de los clientes. Solo un ADMINISTRADOR puede ejecutarlo; los
 * clientes se registran solos desde el sitio público.
 */
public interface ManageClientsUseCase {
  /** Clientes no eliminados, del más reciente al más antiguo, incluidos los inactivos. */
  PageResult<Account> list(Long adminId, PageQuery page);

  /** Un cliente inactivo no puede iniciar sesión y sus tokens vigentes dejan de servir. */
  Account setActive(Long adminId, Long clientId, boolean active);
}
