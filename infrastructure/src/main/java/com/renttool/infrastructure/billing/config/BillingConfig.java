package com.renttool.infrastructure.billing.config;

import com.renttool.application.billing.port.in.ManagePaymentsUseCase;
import com.renttool.application.billing.port.out.*;
import com.renttool.application.billing.service.PaymentService;
import com.renttool.domain.billing.*;
import com.renttool.domain.reservation.Participant;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class BillingConfig {
  @Bean
  ManagePaymentsUseCase payments(
      BillingBookings bookings, PaymentStore store, Clock clock,
      PlatformTransactionManager transactions) {
    var service = new PaymentService(bookings, store, clock);
    var transaction = new TransactionTemplate(transactions);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

    return new ManagePaymentsUseCase() {
      public Payment pay(Participant actor, Long id, boolean approved) {
        return transaction.execute(status -> service.pay(actor, id, approved));
      }
      public Summary get(Participant actor, Long id) {
        return transaction.execute(status -> service.get(actor, id));
      }
      public Invoice invoice(Participant actor, Long id) {
        return transaction.execute(status -> service.invoice(actor, id));
      }
      public void ensureCancellationAllowed(Long id) {
        transaction.executeWithoutResult(status -> service.ensureCancellationAllowed(id));
      }
    };
  }
}
