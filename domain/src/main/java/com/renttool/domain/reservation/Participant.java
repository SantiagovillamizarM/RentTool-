package com.renttool.domain.reservation;

import com.renttool.domain.shared.exception.ForbiddenOperationException;

/**
 * Quién actúa sobre una reserva. Reservas no depende del módulo de identidad: el adaptador de
 * entrada traduce la credencial a este valor.
 */
public record Participant(Long id, Role role) {
  public enum Role {
    CLIENTE,
    PROVEEDOR,
    ADMINISTRADOR
  }

  public static Participant anonymous() {
    return new Participant(null, null);
  }

  public boolean isClient() {
    return id != null && role == Role.CLIENTE;
  }

  public boolean isProvider() {
    return id != null && role == Role.PROVEEDOR;
  }

  public boolean isAdministrator() {
    return id != null && role == Role.ADMINISTRADOR;
  }

  public void ensureClient() {
    if (!isClient()) throw new ForbiddenOperationException("Solo un cliente puede reservar");
  }

  public void ensureAuthenticated() {
    if (id == null) throw new ForbiddenOperationException("Inicia sesión para continuar");
  }
}
