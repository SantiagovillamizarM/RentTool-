package com.renttool.infrastructure.operations.config;

import com.renttool.application.operations.port.in.ManageRentalsUseCase;
import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.operations.service.RentalService;
import com.renttool.application.inventory.port.out.*;
import com.renttool.application.reservation.port.out.ReservationRepository;
import com.renttool.application.billing.port.out.PaymentStore;
import com.renttool.application.shared.*;
import com.renttool.domain.operations.Incident;
import com.renttool.domain.reservation.Participant;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class OperationsConfig {
  @Bean
  ManageRentalsUseCase rentalOperations(ReservationRepository reservations, ToolRepository tools,
      UnitRepository units, PaymentStore payments, RentalRecords records, Clock clock,
      PlatformTransactionManager transactions) {
    var service = new RentalService(reservations, tools, units, payments, records, clock);
    var transaction = new TransactionTemplate(transactions);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return new ManageRentalsUseCase() {
      public Summary get(Participant actor, Long id) { return transaction.execute(s -> service.get(actor, id)); }
      public Summary deliver(Participant actor, Long id, String notes) {
        return transaction.execute(s -> service.deliver(actor, id, notes));
      }
      public boolean deliverIfStarted(Long id) {
        return Boolean.TRUE.equals(transaction.execute(s -> service.deliverIfStarted(id)));
      }
      public java.util.List<Long> startedWithoutDelivery() {
        return transaction.execute(s -> service.startedWithoutDelivery());
      }
      public Summary returnEquipment(Participant actor, Long id, ReturnCommand command) {
        return transaction.execute(s -> service.returnEquipment(actor, id, command));
      }
      public Incident report(Participant actor, Long id, IncidentCommand command) {
        return transaction.execute(s -> service.report(actor, id, command));
      }
      public Incident resolve(Participant actor, Long id, String notes, boolean ready) {
        return transaction.execute(s -> service.resolve(actor, id, notes, ready));
      }
      public PageResult<Incident> openIncidents(Participant actor, PageQuery page) {
        return transaction.execute(s -> service.openIncidents(actor, page));
      }
    };
  }
}
