package com.renttool.application.identity.port.out;

public interface PasswordHasher {
  String hash(String rawPassword);

  boolean matches(String rawPassword, String hash);
}
