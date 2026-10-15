package com.renttool.infrastructure.reservation.config;

import com.renttool.application.reservation.port.out.*;
import com.renttool.application.reservation.service.*;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Ensambla los casos de uso de reservas. El servicio se publica envuelto en transacciones; nunca
 * se expone sin ellas porque aceptar depende del bloqueo de la herramienta.
 */
@Configuration
@EnableScheduling
public class ReservationConfig {
  /** Las fechas de alquiler son días de la zona del negocio, no del servidor. */
  @Bean
  Clock businessClock(@Value("${renttool.time-zone:America/Bogota}") String zone) {
    return Clock.system(ZoneId.of(zone));
  }

  @Bean
  AvailabilityService availabilityService(
      ReservationRepository reservations, RentalInventory inventory, Clock clock) {
    return new AvailabilityService(reservations, inventory, clock);
  }

  @Bean
  TransactionalReservations reservationUseCases(
      ReservationRepository reservations,
      RentalInventory inventory,
      ParticipantDirectory directory,
      Clock clock,
      PlatformTransactionManager transactions) {
    return new TransactionalReservations(
        new ReservationService(reservations, inventory, directory, clock), transactions);
  }
}
