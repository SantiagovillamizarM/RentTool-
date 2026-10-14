package com.renttool.domain.shared.exception;

/** Violación de una regla de negocio. No conoce HTTP: cada adaptador de entrada la traduce. */
public abstract sealed class DomainException extends RuntimeException
    permits InvalidInputException,
        AuthenticationFailedException,
        ForbiddenOperationException,
        NotFoundException,
        ConflictException {
  protected DomainException(String message) {
    super(message);
  }
}
