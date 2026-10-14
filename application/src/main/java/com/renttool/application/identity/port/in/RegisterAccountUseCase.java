package com.renttool.application.identity.port.in;

import com.renttool.domain.identity.Role;

public interface RegisterAccountUseCase {
  AuthenticatedSession register(Command command);

  record Command(String name, String email, String rawPassword, Role role) {}
}
