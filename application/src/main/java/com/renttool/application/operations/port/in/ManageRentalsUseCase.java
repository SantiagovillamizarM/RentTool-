package com.renttool.application.operations.port.in;

import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import com.renttool.domain.reservation.Participant;
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

  record ReturnItem(Long unitId, UnitReturn.Condition condition, String notes) {}
  record ReturnCommand(String notes, List<ReturnItem> units) {}
  record IncidentCommand(Long unitId, Incident.Type type, String description) {}
  record UnitView(Long id, String code, String status, UnitReturn.Condition returnedCondition, String notes) {}
  /**
   * @param status PENDIENTE_ENTREGA, EN_USO, DEVUELTA, NO_APLICA o ENTREGA_BLOQUEADA (ya empezó y
   *     ninguna unidad estaba lista para entregarse)
   * @param canReport el proveedor o el admin tras la entrega, o el cliente mientras tiene el equipo
   */
  record Summary(String status, boolean canManage, boolean paid, boolean canDeliver,
      boolean canReport, RentalOperation operation, List<UnitView> units, List<Incident> incidents) {}
}
