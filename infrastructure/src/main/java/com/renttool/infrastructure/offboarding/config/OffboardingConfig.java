package com.renttool.infrastructure.offboarding.config;

import com.renttool.application.identity.port.out.AccountRepository;
import com.renttool.application.identity.port.out.ProviderProfileRepository;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.offboarding.port.in.RemoveProviderUseCase;
import com.renttool.application.offboarding.service.ProviderRemovalService;
import com.renttool.application.reservation.port.out.ReservationEventRepository;
import com.renttool.application.reservation.port.out.ReservationRepository;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica la baja de proveedores dentro de una sola transacción: cuenta, catálogo y reservas
 * cambian juntos o no cambia nada. READ COMMITTED por la misma razón que las reservas: al obtener
 * el bloqueo de una reserva hay que ver lo que confirmó quien lo tenía antes.
 */
@Configuration
public class OffboardingConfig {
  @Bean
  RemoveProviderUseCase removeProviderUseCase(
      AccountRepository accounts,
      ProviderProfileRepository profiles,
      ToolRepository tools,
      ReservationRepository reservations,
      ReservationEventRepository events,
      Clock clock,
      PlatformTransactionManager transactions) {
    var service =
        new ProviderRemovalService(accounts, profiles, tools, reservations, events, clock);
    var write = new TransactionTemplate(transactions);
    write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    var read = new TransactionTemplate(transactions);
    read.setReadOnly(true);
    return new RemoveProviderUseCase() {
      @Override
      public Summary preview(Long adminId, Long providerId) {
        return read.execute(s -> service.preview(adminId, providerId));
      }

      @Override
      public Summary remove(Long adminId, Long providerId) {
        return write.execute(s -> service.remove(adminId, providerId));
      }
    };
  }
}
