package com.renttool.infrastructure.operations.config;

import com.renttool.application.operations.port.in.ManageRentalsUseCase;
import com.renttool.application.reservation.port.in.ExpireReservationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Registra sola la entrega de las reservas aceptadas el día en que empiezan y cancela con reembolso
 * las que terminaron sin que se pudiera entregar el equipo. Cada reserva va en su propia
 * transacción: si una falla, las demás siguen.
 */
@Component
class AutomaticDeliveryJob {
  private static final Logger log = LoggerFactory.getLogger(AutomaticDeliveryJob.class);
  private final ManageRentalsUseCase rentals;
  private final ExpireReservationsUseCase reservations;

  AutomaticDeliveryJob(ManageRentalsUseCase rentals, ExpireReservationsUseCase reservations) {
    this.rentals = rentals;
    this.reservations = reservations;
  }

  @Scheduled(
      initialDelayString = "${renttool.reservations.delivery-check:PT10S}",
      fixedDelayString = "${renttool.reservations.delivery-check:PT5M}")
  void run() {
    int delivered = 0;
    for (var id : rentals.startedWithoutDelivery()) {
      try {
        if (rentals.deliverIfStarted(id)) delivered++;
      } catch (RuntimeException e) {
        log.warn("No se pudo registrar la entrega automática de la reserva {}: {}", id, e.getMessage());
      }
    }
    if (delivered > 0) log.info("{} entregas registradas automáticamente", delivered);
    int cancelled = reservations.cancelUndeliveredEnded();
    if (cancelled > 0) log.info("{} reservas sin entrega se cancelaron y reembolsaron", cancelled);
  }
}
