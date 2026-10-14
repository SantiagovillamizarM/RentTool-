package com.renttool.domain.shared.exception;

/** El recurso solicitado no existe. */
public final class NotFoundException extends DomainException {
  public NotFoundException(String message) {
    super(message);
  }
}
