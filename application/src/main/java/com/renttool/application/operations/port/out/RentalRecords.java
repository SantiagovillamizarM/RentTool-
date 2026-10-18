package com.renttool.application.operations.port.out;

import com.renttool.domain.operations.*;
import com.renttool.application.shared.*;
import java.util.*;

public interface RentalRecords {
  Optional<RentalOperation> operation(Long reservationId);
  void save(RentalOperation operation);
  List<UnitReturn> returns(Long reservationId);
  void save(UnitReturn item);
  List<Incident> incidents(Long reservationId);
  Optional<Incident> incident(Long id);
  Incident save(Incident incident);
  boolean hasBlockingIncident(Long unitId);
  Set<Long> outUnitIds(Long toolId);
  PageResult<Incident> openIncidents(PageQuery page);
}
