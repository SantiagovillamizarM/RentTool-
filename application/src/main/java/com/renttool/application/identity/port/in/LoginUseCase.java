package com.renttool.application.identity.port.in;

public interface LoginUseCase {
  AuthenticatedSession login(String email, String rawPassword);
}
