package com.renttool.domain.shared.exception;

/** El actor no tiene permiso para la operación. */
public final class ForbiddenOperationException extends DomainException {
  public ForbiddenOperationException(String message) {
    super(message);
  }
}
