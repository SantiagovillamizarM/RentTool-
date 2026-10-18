package com.renttool.infrastructure.operations.persistence;

import com.renttool.application.operations.port.out.RentalRecords;
import com.renttool.application.shared.*;
import com.renttool.domain.operations.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;

@Repository
class RentalRecordsAdapter implements RentalRecords {
  private final RentalOperationJpaRepository operations;
  private final UnitReturnJpaRepository returns;
  private final IncidentJpaRepository incidents;

  RentalRecordsAdapter(RentalOperationJpaRepository operations, UnitReturnJpaRepository returns,
      IncidentJpaRepository incidents) {
    this.operations = operations; this.returns = returns; this.incidents = incidents;
  }

  public Optional<RentalOperation> operation(Long id) {
    return operations.findById(id).map(this::domain);
  }

  public void save(RentalOperation operation) {
    var entity = new RentalOperationEntity();
    entity.reservationId = operation.reservationId();
    entity.deliveredAt = operation.deliveredAt(); entity.deliveredBy = operation.deliveredBy();
    entity.deliveryNotes = operation.deliveryNotes(); entity.returnedAt = operation.returnedAt();
    entity.returnedBy = operation.returnedBy(); entity.returnNotes = operation.returnNotes();
    operations.saveAndFlush(entity);
  }

  public List<UnitReturn> returns(Long id) {
    return returns.findByReservationIdOrderByUnitId(id).stream()
        .map(entity -> new UnitReturn(entity.reservationId, entity.unitId,
            UnitReturn.Condition.valueOf(entity.condition), entity.notes)).toList();
  }

  public void save(UnitReturn item) {
    var entity = new UnitReturnEntity();
    entity.reservationId = item.reservationId(); entity.unitId = item.unitId();
    entity.condition = item.condition().name(); entity.notes = item.notes();
    returns.saveAndFlush(entity);
  }

  public List<Incident> incidents(Long id) {
    return incidents.findByReservationIdOrderByReportedAtAscIdAsc(id).stream().map(this::domain).toList();
  }
  public Optional<Incident> incident(Long id) { return incidents.findById(id).map(this::domain); }
  public Incident save(Incident incident) {
    var entity = new IncidentEntity();
    entity.id = incident.id(); entity.reservationId = incident.reservationId(); entity.unitId = incident.unitId();
    entity.type = incident.type().name(); entity.description = incident.description();
    entity.reportedBy = incident.reportedBy(); entity.reportedAt = incident.reportedAt();
    entity.resolvedBy = incident.resolvedBy(); entity.resolvedAt = incident.resolvedAt();
    entity.resolution = incident.resolution();
    return domain(incidents.saveAndFlush(entity));
  }

  public boolean hasBlockingIncident(Long id) {
    return incidents.existsByUnitIdAndResolvedAtIsNullAndTypeIn(id, List.of("DANO", "PERDIDA"));
  }
  public Set<Long> outUnitIds(Long toolId) { return new HashSet<>(operations.outUnitIds(toolId)); }
  public PageResult<Incident> openIncidents(PageQuery page) {
    var result = incidents.findByResolvedAtIsNull(
        PageRequest.of(page.page(), page.size(), Sort.by("reportedAt", "id").descending()));
    return new PageResult<>(result.getContent().stream().map(this::domain).toList(),
        result.getTotalElements(), result.getTotalPages(), page.page());
  }

  private RentalOperation domain(RentalOperationEntity entity) {
    return new RentalOperation(entity.reservationId, entity.deliveredAt, entity.deliveredBy,
        entity.deliveryNotes, entity.returnedAt, entity.returnedBy, entity.returnNotes);
  }
  private Incident domain(IncidentEntity entity) {
    return new Incident(entity.id, entity.reservationId, entity.unitId, Incident.Type.valueOf(entity.type),
        entity.description, entity.reportedBy, entity.reportedAt, entity.resolvedBy, entity.resolvedAt, entity.resolution);
  }
}
