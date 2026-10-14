package com.renttool.domain.shared.exception;

/** Datos que incumplen una regla del dominio. */
public final class InvalidInputException extends DomainException {
  public InvalidInputException(String message) {
    super(message);
  }
}
