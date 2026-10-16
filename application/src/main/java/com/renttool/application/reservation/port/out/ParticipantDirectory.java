package com.renttool.application.reservation.port.out;

/** Datos visibles de las personas que participan en una reserva. */
public interface ParticipantDirectory {
  String nameOf(Long participantId);

  /** Ficha para informes. {@code email} y {@code taxId} (NIT del proveedor) pueden faltar. */
  default Card cardOf(Long participantId) {
    return new Card(nameOf(participantId), null, null);
  }

  record Card(String name, String email, String taxId) {}
}
