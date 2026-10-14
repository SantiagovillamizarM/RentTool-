package com.renttool.domain.shared.exception;

/** La operación choca con el estado actual. */
public final class ConflictException extends DomainException {
  public ConflictException(String message) {
    super(message);
  }
}
