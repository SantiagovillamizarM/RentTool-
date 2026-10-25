package com.renttool.application.operations.port.in;

import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import com.renttool.domain.reservation.Participant;
import java.math.BigDecimal;
import java.util.List;

public interface ManageRentalsUseCase {
  Summary get(Participant actor, Long reservationId);
  Summary deliver(Participant actor, Long reservationId, String notes);
  /**
   * Entrega automática: registra la entrega de la reserva si está aceptada, pagada y ya llegó su
   * día de inicio. Si una unidad no está lista, intenta reemplazarla por otra libre. Dice si la
   * registró.
   */
  boolean deliverIfStarted(Long reservationId);
  /** Ids de las reservas aceptadas que ya empezaron y todavía no tienen entrega. */
  List<Long> startedWithoutDelivery();
  Summary returnEquipment(Participant actor, Long reservationId, ReturnCommand command);
  Incident report(Participant actor, Long reservationId, IncidentCommand command);
  Incident resolve(Participant actor, Long incidentId, String resolution, boolean readyToUse);
  PageResult<Incident> openIncidents(Participant actor, PageQuery page);

  /** @param charge cobro por una unidad dañada o perdida; null equivale a cero */
  record ReturnItem(Long unitId, UnitReturn.Condition condition, String notes, BigDecimal charge) {}
  record ReturnCommand(String notes, List<ReturnItem> units) {}
  record IncidentCommand(Long unitId, Incident.Type type, String description) {}
  record UnitView(Long id, String code, String status, UnitReturn.Condition returnedCondition,
      String notes, BigDecimal charge) {}
  /**
   * Liquidación al devolver. {@code total} suma el recargo por retraso, los cobros por daño o
   * pérdida y el saldo de una extensión aún no pagado. El depósito de garantía los cubre: lo que
   * sobra se devuelve ({@code depositRefund}) y lo que falta lo paga el cliente ({@code amountDue}).
   */
  record Charges(int lateDays, BigDecimal lateFee, BigDecimal damageCharges,
      BigDecimal extensionBalance, BigDecimal total, BigDecimal deposit, BigDecimal depositRefund,
      BigDecimal amountDue) {}
  /**
   * @param status PENDIENTE_ENTREGA, EN_USO, DEVUELTA, NO_APLICA o ENTREGA_BLOQUEADA (ya empezó y
   *     ninguna unidad estaba lista para entregarse)
   * @param canReport el proveedor o el admin tras la entrega, o el cliente mientras tiene el equipo
   */
  record Summary(String status, boolean canManage, boolean paid, boolean canDeliver,
      boolean canReport, RentalOperation operation, List<UnitView> units, List<Incident> incidents,
      Charges charges) {}
}
