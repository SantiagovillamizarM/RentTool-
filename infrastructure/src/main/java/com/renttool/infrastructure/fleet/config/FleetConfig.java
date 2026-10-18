package com.renttool.infrastructure.fleet.config;

import com.renttool.application.fleet.port.in.SetUnitCountUseCase;
import com.renttool.application.fleet.service.UnitCountService;
import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.application.inventory.port.out.UnitRepository;
import com.renttool.application.reservation.port.out.ReservationRepository;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica el ajuste de cantidad de unidades dentro de una transacción. READ COMMITTED, como al
 * aceptar reservas: tras obtener el bloqueo de la herramienta hay que ver las asignaciones que
 * confirmó quien lo tenía antes.
 */
@Configuration
public class FleetConfig {
  @Bean
  SetUnitCountUseCase setUnitCountUseCase(
      ToolRepository tools,
      UnitRepository units,
      ReservationRepository reservations,
      Clock clock,
      PlatformTransactionManager transactions,
      com.renttool.application.operations.port.out.RentalRecords records) {
    var service = new UnitCountService(tools, units, reservations, clock, records);
    var tx = new TransactionTemplate(transactions);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    return (actor, toolId, count) -> tx.execute(s -> service.setCount(actor, toolId, count));
  }
}
