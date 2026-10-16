package com.renttool.infrastructure.reservation.config;

import com.renttool.application.reservation.port.in.ReservationReportUseCase;
import com.renttool.application.reservation.port.out.*;
import com.renttool.domain.reservation.Participant;
import com.renttool.domain.reservation.ReservationStatus;
import java.util.List;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
      ReservationEventRepository events,
      RentalInventory inventory,
      ParticipantDirectory directory,
      Clock clock,
      PlatformTransactionManager transactions) {
    return new TransactionalReservations(
        new ReservationService(reservations, events, inventory, directory, clock), transactions);
  }

  /** Los informes leen mucho y escriben el registro de descarga en la misma transacción. */
  @Bean
  ReservationReportUseCase reservationReports(
      ReservationRepository reservations,
      ReservationEventRepository events,
      RentalInventory inventory,
      ParticipantDirectory directory,
      ReportDownloadLog log,
      Clock clock,
      PlatformTransactionManager transactions) {
    var service =
        new ReservationReportService(reservations, events, inventory, directory, log, clock);
    var tx = new TransactionTemplate(transactions);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return new ReservationReportUseCase() {
      @Override
      public List<ReservationRow> reservations(
          Participant admin, Range range, ReservationStatus status) {
        return tx.execute(s -> service.reservations(admin, range, status));
      }

      @Override
      public List<EventRow> events(Participant admin, Range range) {
        return tx.execute(s -> service.events(admin, range));
      }
    };
  }
}
