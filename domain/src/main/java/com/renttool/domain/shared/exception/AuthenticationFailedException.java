package com.renttool.domain.shared.exception;

/** Credenciales o cuenta no válidas. */
public final class AuthenticationFailedException extends DomainException {
  public AuthenticationFailedException(String message) {
    super(message);
  }
}
