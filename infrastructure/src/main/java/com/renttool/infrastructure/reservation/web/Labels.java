package com.renttool.infrastructure.reservation.web;

import com.renttool.domain.reservation.ReservationEvent.ActorRole;
import com.renttool.domain.reservation.ReservationStatus;

/** Textos en español para estados y roles en informes. */
final class Labels {
  private Labels() {}

  static String status(ReservationStatus s) {
    return switch (s) {
      case SOLICITADA -> "Solicitada";
      case ACEPTADA -> "Aceptada";
      case RECHAZADA -> "Rechazada";
      case CANCELADA -> "Cancelada";
      case VENCIDA -> "Vencida";
    };
  }

  static String role(ActorRole r) {
    return switch (r) {
      case CLIENTE -> "Cliente";
      case PROVEEDOR -> "Proveedor";
      case ADMINISTRADOR -> "Administrador";
      case SISTEMA -> "Sistema";
    };
  }
}
