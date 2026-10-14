package com.renttool.infrastructure.identity.security;

import com.renttool.application.identity.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasher implements PasswordHasher {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String hash) {
    return encoder.matches(rawPassword, hash);
  }
}
