package com.renttool.application.identity.service;

import com.renttool.domain.shared.exception.InvalidInputException;

/** Longitud admitida para contraseñas elegidas por usuarios o asignadas por el administrador. */
final class PasswordPolicy {
  static final int MIN = 10;
  static final int MAX = 64;

  private PasswordPolicy() {}

  static void check(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN || rawPassword.length() > MAX)
      throw new InvalidInputException(
          "La contraseña requiere entre " + MIN + " y " + MAX + " caracteres");
  }
}
