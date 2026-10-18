package com.renttool.application.operations.port.in;

import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import com.renttool.domain.reservation.Participant;
import java.util.List;

public interface ManageRentalsUseCase {
  Summary get(Participant actor, Long reservationId);
  Summary deliver(Participant actor, Long reservationId, String notes);
  Summary returnEquipment(Participant actor, Long reservationId, ReturnCommand command);
  Incident report(Participant actor, Long reservationId, IncidentCommand command);
  Incident resolve(Participant actor, Long incidentId, String resolution, boolean readyToUse);
  PageResult<Incident> openIncidents(Participant actor, PageQuery page);

  record ReturnItem(Long unitId, UnitReturn.Condition condition, String notes) {}
  record ReturnCommand(String notes, List<ReturnItem> units) {}
  record IncidentCommand(Long unitId, Incident.Type type, String description) {}
  record UnitView(Long id, String code, String status, UnitReturn.Condition returnedCondition, String notes) {}
  record Summary(String status, boolean canManage, boolean paid, boolean canDeliver,
      RentalOperation operation, List<UnitView> units, List<Incident> incidents) {}
}
