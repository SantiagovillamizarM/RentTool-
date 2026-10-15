package com.renttool.infrastructure.reservation.config;

import com.renttool.application.reservation.port.in.ExpireReservationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Revisa periódicamente las solicitudes que el proveedor no respondió a tiempo. */
@Component
class ReservationExpiryJob {
  private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);
  private final ExpireReservationsUseCase reservations;

  ReservationExpiryJob(ExpireReservationsUseCase reservations) {
    this.reservations = reservations;
  }

  @Scheduled(
      initialDelayString = "${renttool.reservations.expiry-check:PT5M}",
      fixedDelayString = "${renttool.reservations.expiry-check:PT5M}")
  void expireOverdue() {
    int expired = reservations.expireOverdue();
    if (expired > 0) log.info("{} solicitudes de reserva vencieron sin respuesta", expired);
  }
}
