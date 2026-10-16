package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Role;

public interface RegisterAccountUseCase {
  /** Registro público: solo crea clientes. */
  AuthenticatedSession register(Command command);

  /** {@code role} es opcional; si se envía debe ser CLIENTE. */
  record Command(String name, String email, String rawPassword, Role role) {}
}
